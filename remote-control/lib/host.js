// dsh-remote-control host plugin — static-token edition.
//
// Remote-control surface for driving a DSH agent from a phone. Designed to sit
// behind a private network (Tailscale), where a single shared token is enough:
//
//   GET  /remote               mobile web page
//   POST /remote/task          { text, workspace? } -> creates a session+agent,
//                              queues the task, returns { sessionId }
//   GET  /remote/events        SSE stream of one session's live events
//   GET  /remote/sessions      list live agents
//
// Every action route is guarded by the token (Authorization: Bearer or ?token=).
// The token is read from config.token, else <DSH_HOME>/remote-control.token
// (created on first boot when absent).
//
// Routes are registered only where `webServer` exists (the web profile) via a
// scoped inject, so the plugin never waits on a headless-only service.

import { readFileSync, writeFileSync, createReadStream } from 'node:fs'
import { readdir, stat, unlink, rm, rename, copyFile } from 'node:fs/promises'
import { randomBytes, randomUUID } from 'node:crypto'
import { homedir } from 'node:os'
import { join, basename, dirname, extname } from 'node:path'
import { createUserMessage } from '@deepseek-ai/dsh-llm'
import { installModelSelection } from '@deepseek-ai/dsh-agent'

export const name = 'remote-control'
export const inject = ['agents']

const PAGE_HTML = readFileSync(new URL('./page.html', import.meta.url), 'utf8')

const DSH_HOME = process.env.DSH_HOME || join(homedir(), '.dsh')
const TOKEN_FILE = join(DSH_HOME, 'remote-control.token')

function resolveToken(config) {
  if (config && typeof config.token === 'string' && config.token) return config.token
  try {
    const existing = readFileSync(TOKEN_FILE, 'utf8').trim()
    if (existing) return existing
  } catch {}
  const token = randomBytes(24).toString('base64url')
  try { writeFileSync(TOKEN_FILE, token + '\n', { mode: 0o600 }) } catch {}
  console.error('[remote-control] access token: ' + token)
  return token
}

function textOf(content) {
  if (!Array.isArray(content)) return ''
  let out = ''
  for (const block of content) {
    if (block && block.type === 'text' && typeof block.text === 'string') out += block.text
  }
  return out
}

function mapEvent(event) {
  const d = event.data
  const base = { seq: event.seq, type: event.type }
  switch (event.type) {
    case 'turn/start':
      return { ...base, turn: d.turn }
    case 'turn/end':
      return { ...base, turn: d.turn, reason: d.reason && d.reason.kind }
    case 'user/message':
      return { ...base, text: textOf(d.content) }
    case 'assistant/message':
      return { ...base, turn: d.turn, step: d.step, text: textOf(d.message && d.message.content) }
    case 'tool/call':
      return { ...base, turn: d.turn, step: d.step, tool: d.name, arguments: d.arguments }
    case 'tool/result':
      return { ...base, turn: d.turn, step: d.step, isError: !!d.error, text: textOf(d.message && d.message.content) }
    case 'todo/write':
      return { ...base, todos: d.todos }
    default:
      return base
  }
}

function authorized(req, token) {
  if (!token) return true
  const header = req.headers.authorization
  if (header && header === 'Bearer ' + token) return true
  const url = new URL(req.url, 'http://localhost')
  return url.searchParams.get('token') === token
}

async function readJson(req, maxBytes = 1_000_000) {
  const chunks = []
  let total = 0
  for await (const chunk of req) {
    total += chunk.length
    if (total > maxBytes) throw new Error('body too large')
    chunks.push(chunk)
  }
  const raw = Buffer.concat(chunks).toString('utf8')
  return raw ? JSON.parse(raw) : {}
}

function sendJson(res, status, obj) {
  res.writeHead(status, { 'content-type': 'application/json; charset=utf-8' })
  res.end(JSON.stringify(obj))
}

const CONTENT_TYPES = {
  '.png': 'image/png', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg', '.gif': 'image/gif',
  '.webp': 'image/webp', '.svg': 'image/svg+xml', '.bmp': 'image/bmp',
  '.apk': 'application/vnd.android.package-archive',
  '.pdf': 'application/pdf',
  '.txt': 'text/plain; charset=utf-8', '.md': 'text/plain; charset=utf-8', '.json': 'application/json',
  '.mp4': 'video/mp4', '.mp3': 'audio/mpeg', '.wav': 'audio/wav',
  '.zip': 'application/zip', '.gz': 'application/gzip',
}

function contentTypeFor(path) {
  const ext = extname(path).toLowerCase()
  return CONTENT_TYPES[ext] || 'application/octet-stream'
}

async function pathExists(p) {
  try { await stat(p); return true } catch { return false }
}

export function apply(ctx, config = {}) {
  const agents = ctx.agents
  const token = resolveToken(config)
  const defaultModel = ctx.get('agentDefaultModel')
  const modelSelection = defaultModel ? defaultModel.currentSelection() : undefined

  const handles = new Map() // sessionId -> AgentHandle

  ctx.effect(() => () => {
    for (const handle of handles.values()) { try { void handle.dispose() } catch {} }
    handles.clear()
  })

  if (typeof ctx.inject === 'function') {
    ctx.inject(['webServer'], (scope) => {
      registerRoutes(scope, ctx, { agents, token, handles, modelSelection })
    })
  }
}

function registerRoutes(scope, ctx, { agents, token, handles, modelSelection }) {
  const webServer = scope.webServer

  webServer.register({
    kind: 'exact',
    path: '/remote',
    handler: (req, res) => {
      res.writeHead(200, { 'content-type': 'text/html; charset=utf-8', 'cache-control': 'no-store' })
      res.end(PAGE_HTML)
    },
  })

  webServer.register({
    kind: 'exact',
    path: '/remote/task',
    handler: async (req, res) => {
      if (req.method !== 'POST') { res.writeHead(405); return res.end() }
      if (!authorized(req, token)) { sendJson(res, 401, { error: 'unauthorized' }); return }
      try {
        const body = await readJson(req)
        const text = String((body && body.text) || '').trim()
        if (!text) { sendJson(res, 400, { error: 'text is required' }); return }
        const sessionId = randomUUID()
        const cwd = (body && typeof body.workspace === 'string' && body.workspace) ? body.workspace : process.cwd()
        const options = { sessionId, meta: { cwd } }
        if (modelSelection) {
          options.agentOptions = { provider: modelSelection.provider, model: modelSelection.model }
          options.setup = (agentCtx) => {
            installModelSelection(agentCtx, { current: modelSelection, assembled: undefined })
          }
        }
        const handle = await agents.create(options)
        handles.set(sessionId, handle)
        handle.agent.followup(createUserMessage({
          content: [{ type: 'text', text }],
          source: { kind: 'user' },
        }))
        sendJson(res, 200, { sessionId })
      } catch (error) {
        sendJson(res, 500, { error: String((error && error.message) || error) })
      }
    },
  })

  webServer.register({
    kind: 'exact',
    path: '/remote/events',
    handler: async (req, res) => {
      if (!authorized(req, token)) { sendJson(res, 401, { error: 'unauthorized' }); return }
      const sessionId = new URL(req.url, 'http://localhost').searchParams.get('sessionId')
      if (!sessionId) { sendJson(res, 400, { error: 'sessionId is required' }); return }

      res.writeHead(200, {
        'content-type': 'text/event-stream; charset=utf-8',
        'cache-control': 'no-cache, no-transform',
        connection: 'keep-alive',
      })
      const write = (obj) => { try { res.write('data: ' + JSON.stringify(obj) + '\n\n') } catch {} }
      write({ kind: 'hello', sessionId })

      const persistence = ctx.get('sessionPersistence')
      if (persistence) {
        try {
          const { events } = await persistence.readFrom(sessionId, 0)
          for (const event of events) write(mapEvent(event))
        } catch {}
      }

      const offs = []
      offs.push(ctx.on('session/event', (session, event) => {
        if (session.id !== sessionId) return
        write(mapEvent(event))
      }))
      offs.push(ctx.on('agent/status', (payload) => {
        if (payload && payload.agent && payload.agent.id === sessionId) {
          write({ kind: 'status', status: payload.status })
        }
      }))

      req.on('close', () => {
        for (const off of offs) { try { off() } catch {} }
      })
    },
  })

  webServer.register({
    kind: 'exact',
    path: '/remote/sessions',
    handler: (req, res) => {
      if (!authorized(req, token)) { sendJson(res, 401, { error: 'unauthorized' }); return }
      const sessions = agents.list().map((agent) => ({ id: agent.id, status: agent.status }))
      sendJson(res, 200, { sessions })
    },
  })

  // Read-only file browser for the native app (same trust as /api/*, no token).
  webServer.register({
    kind: 'exact',
    path: '/remote/list',
    handler: async (req, res) => {
      if (req.method !== 'GET') { res.writeHead(405); return res.end() }
      try {
        const url = new URL(req.url, 'http://localhost')
        const raw = (url.searchParams.get('path') || '').trim()

        // Root selection: with no path, enumerate drive roots (Windows).
        if (raw === '') {
          const drives = []
          for (let c = 65; c <= 90; c += 1) {
            const letter = String.fromCharCode(c)
            const root = `${letter}:\\`
            try { await stat(root) } catch { continue }
            drives.push({ name: root, path: root, isDirectory: true, size: 0, mtime: 0 })
          }
          sendJson(res, 200, { path: '', parent: '', drives, entries: drives })
          return
        }

        const target = raw
        const st = await stat(target)
        if (!st.isDirectory()) { sendJson(res, 400, { error: 'not a directory' }); return }

        // A drive root (e.g. "C:\") is a drive-selector stop; show the drive
        // list so the user can navigate to another (D:, E:, ...) drive.
        const isDriveRoot = /^[A-Za-z]:\\?$/.test(target)
        if (isDriveRoot) {
          const drives = []
          for (let c = 65; c <= 90; c += 1) {
            const letter = String.fromCharCode(c)
            const root = `${letter}:\\`
            try { await stat(root) } catch { continue }
            drives.push({ name: root, path: root, isDirectory: true, size: 0, mtime: 0 })
          }
          sendJson(res, 200, { path: '', parent: '', drives, entries: drives })
          return
        }

        const dirents = await readdir(target, { withFileTypes: true })
        const entries = []
        for (const d of dirents) {
          const full = join(target, d.name)
          let isDirectory = d.isDirectory()
          let size = 0
          let mtime = 0
          try {
            const s = await stat(full)
            isDirectory = s.isDirectory()
            size = s.size
            mtime = s.mtimeMs
          } catch {}
          entries.push({ name: d.name, path: full, isDirectory, size, mtime })
        }
        entries.sort((a, b) =>
          a.isDirectory === b.isDirectory
            ? a.name.localeCompare(b.name)
            : a.isDirectory ? -1 : 1)
        // Normal directory: parent is its parent dir, unless it's a drive root
        // (handled above, where parent is sent as '' to signal the selector).
        const parent = dirname(target)
        sendJson(res, 200, { path: target, parent, entries })
      } catch (e) {
        sendJson(res, 500, { error: String((e && e.message) || e) })
      }
    },
  })

  webServer.register({
    kind: 'exact',
    path: '/remote/file',
    handler: async (req, res) => {
      if (req.method !== 'GET') { res.writeHead(405); return res.end() }
      try {
        const url = new URL(req.url, 'http://localhost')
        const target = (url.searchParams.get('path') || '').trim()
        if (!target) { sendJson(res, 400, { error: 'path is required' }); return }
        const st = await stat(target)
        if (!st.isFile()) { sendJson(res, 400, { error: 'not a file' }); return }
        res.writeHead(200, {
          'content-type': contentTypeFor(target),
          'content-length': st.size,
          'content-disposition': `inline; filename*=UTF-8''${encodeURIComponent(basename(target))}`,
        })
        createReadStream(target).pipe(res)
      } catch (e) {
        sendJson(res, 500, { error: String((e && e.message) || e) })
      }
    },
  })

  // Upload a file into a workspace directory (base64 JSON body, ~30MB cap).
  webServer.register({
    kind: 'exact',
    path: '/remote/upload',
    handler: async (req, res) => {
      if (req.method !== 'POST') { res.writeHead(405); return res.end() }
      try {
        const body = await readJson(req, 40_000_000)
        const dir = String((body && body.dir) || '').trim() || process.cwd()
        const name = String((body && body.name) || '').trim()
        const data = (body && body.data) || ''
        if (!name) { sendJson(res, 400, { error: 'name is required' }); return }
        if (!data) { sendJson(res, 400, { error: 'data is required' }); return }
        if (name.includes('/') || name.includes('\\') || name === '.' || name === '..') {
          sendJson(res, 400, { error: 'invalid name' }); return
        }
        const st = await stat(dir)
        if (!st.isDirectory()) { sendJson(res, 400, { error: 'not a directory' }); return }
        const target = join(dir, name)
        writeFileSync(target, Buffer.from(data, 'base64'))
        sendJson(res, 200, { path: target })
      } catch (e) {
        sendJson(res, 500, { error: String((e && e.message) || e) })
      }
    },
  })

  // Delete a file or directory in the workspace (POST { path }).
  webServer.register({
    kind: 'exact',
    path: '/remote/delete',
    handler: async (req, res) => {
      if (req.method !== 'POST') { res.writeHead(405); return res.end() }
      try {
        const body = await readJson(req)
        const target = String((body && body.path) || '').trim()
        if (!target) { sendJson(res, 400, { error: 'path is required' }); return }
        const st = await stat(target)
        if (st.isDirectory()) {
          await rm(target, { recursive: true, force: true })
        } else {
          await unlink(target)
        }
        sendJson(res, 200, { deleted: target })
      } catch (e) {
        sendJson(res, 500, { error: String((e && e.message) || e) })
      }
    },
  })

  // Rename a file or directory (POST { path, name }) — stays in the same directory.
  webServer.register({
    kind: 'exact',
    path: '/remote/rename',
    handler: async (req, res) => {
      if (req.method !== 'POST') { res.writeHead(405); return res.end() }
      try {
        const body = await readJson(req)
        const target = String((body && body.path) || '').trim()
        const name = String((body && body.name) || '').trim()
        if (!target || !name) { sendJson(res, 400, { error: 'path and name are required' }); return }
        if (name.includes('/') || name.includes('\\') || name === '.' || name === '..') {
          sendJson(res, 400, { error: 'invalid name' }); return
        }
        const next = join(dirname(target), name)
        await rename(target, next)
        sendJson(res, 200, { path: next })
      } catch (e) {
        sendJson(res, 500, { error: String((e && e.message) || e) })
      }
    },
  })

  // Duplicate a file (POST { path }) → "<base>-copy<ext>" with auto-increment.
  webServer.register({
    kind: 'exact',
    path: '/remote/copy',
    handler: async (req, res) => {
      if (req.method !== 'POST') { res.writeHead(405); return res.end() }
      try {
        const body = await readJson(req)
        const target = String((body && body.path) || '').trim()
        if (!target) { sendJson(res, 400, { error: 'path is required' }); return }
        const ext = extname(target)
        const base = target.slice(0, target.length - ext.length)
        let dest = `${base}-copy${ext}`
        let n = 2
        while (await pathExists(dest)) { dest = `${base}-copy-${n}${ext}`; n += 1 }
        await copyFile(target, dest)
        sendJson(res, 200, { path: dest })
      } catch (e) {
        sendJson(res, 500, { error: String((e && e.message) || e) })
      }
    },
  })

  // Create a folder (POST { dir, name }) in a directory, then return its path.
  webServer.register({
    kind: 'exact',
    path: '/remote/mkdir',
    handler: async (req, res) => {
      if (req.method !== 'POST') { res.writeHead(405); return res.end() }
      try {
        const body = await readJson(req)
        const dir = String((body && body.dir) || '').trim()
        const name = String((body && body.name) || '').trim()
        if (!name) { sendJson(res, 400, { error: 'name is required' }); return }
        if (name.includes('/') || name.includes('\\') || name === '.' || name === '..') {
          sendJson(res, 400, { error: 'invalid name' }); return
        }
        const st = await stat(dir)
        if (!st.isDirectory()) { sendJson(res, 400, { error: 'not a directory' }); return }
        const target = join(dir, name)
        const { mkdir } = await import('node:fs/promises')
        await mkdir(target)
        sendJson(res, 200, { path: target })
      } catch (e) {
        sendJson(res, 500, { error: String((e && e.message) || e) })
      }
    },
  })
}
