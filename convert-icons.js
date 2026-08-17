const fs = require('fs');
const path = require('path');

const UI_DIR = 'C:/Users/SCS/Desktop/dsh/8.16/deepseek-harness-all-icons-with-descriptions/ui-icons';
const BRAND_DIR = 'C:/Users/SCS/Desktop/dsh/8.16/deepseek-harness-all-icons-with-descriptions/brand';
const OUT_DIR = 'C:/Users/SCS/Desktop/dsh/8.16/DshRemote/app/src/main/res/drawable';

const SKIP = new Set(['IconAgentPresetOutline16', 'IconCordisPluginOutline14']); // mask/clipPath-based

function toSnake(name) {
  let s = name.replace(/^Icon/, '');
  s = s.replace(/([a-z0-9])([A-Z])/g, '$1_$2');
  s = s.replace(/([A-Z]+)([A-Z][a-z])/g, '$1_$2');
  return s.toLowerCase().replace(/^_/, '');
}

function attr(el, name) {
  const m = el.match(new RegExp(name + '="([^"]*)"'));
  return m ? m[1] : undefined;
}

function transformGroup(transform, inner) {
  if (!transform) return inner;
  const tm = transform.match(/translate\(\s*([-\d.]+)\s*(?:[, ]\s*([-\d.]+))?\s*\)/);
  if (!tm) return inner;
  const tx = tm[1];
  const ty = tm[2] || '0';
  return `<group android:translateX="${tx}" android:translateY="${ty}">
        ${inner}
        </group>`;
}

function convert(svg, viewBox) {
  const parts = viewBox.split(/[\s,]+/).filter(Boolean).map(Number);
  const vw = Math.round(parts[2] || 16);
  const vh = Math.round(parts[3] || 16);
  const pathRe = /<path\b[^>]*\/?>/g;
  const paths = svg.match(pathRe) || [];
  const children = [];
  for (const p of paths) {
    const d = attr(p, 'd');
    if (!d) continue;
    const stroke = attr(p, 'stroke');
    const sw = attr(p, 'stroke-width');
    const fillRule = attr(p, 'fill-rule');
    const opacity = attr(p, 'opacity');
    const linecap = attr(p, 'stroke-linecap');
    const linejoin = attr(p, 'stroke-linejoin');
    const transform = attr(p, 'transform');
    const isStroke = stroke && stroke !== 'none';
    let a = `        android:pathData="${d}"`;
    if (isStroke) {
      a += `\n            android:strokeColor="#FF000000"`;
      if (sw) a += `\n            android:strokeWidth="${sw}"`;
      if (linecap) a += `\n            android:strokeLineCap="${linecap}"`;
      if (linejoin) a += `\n            android:strokeLineJoin="${linejoin}"`;
      if (opacity) a += `\n            android:strokeAlpha="${opacity}"`;
    } else {
      a += `\n            android:fillColor="#FF000000"`;
      if (opacity) a += `\n            android:fillAlpha="${opacity}"`;
    }
    if (fillRule === 'evenodd') a += `\n            android:fillType="evenOdd"`;
    children.push(transformGroup(transform, `<path\n${a} />`));
  }
  if (children.length === 0) return null;
  return `<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="${vw}dp"
    android:height="${vh}dp"
    android:viewportWidth="${vw}"
    android:viewportHeight="${vh}">
${children.join('\n')}
</vector>
`;
}

if (!fs.existsSync(OUT_DIR)) fs.mkdirSync(OUT_DIR, { recursive: true });

let made = 0;
let skipped = [];

for (const file of fs.readdirSync(UI_DIR).filter(f => f.endsWith('.svg'))) {
  const name = file.replace(/\.svg$/, '');
  if (SKIP.has(name)) { skipped.push(name); continue; }
  const svg = fs.readFileSync(path.join(UI_DIR, file), 'utf8');
  const viewBox = attr(svg, 'viewBox') || '0 0 16 16';
  const xml = convert(svg, viewBox);
  if (!xml) { skipped.push(name + ' (no paths)'); continue; }
  fs.writeFileSync(path.join(OUT_DIR, 'dsh_ic_' + toSnake(name) + '.xml'), xml);
  made++;
}

for (const file of ['FishLogo.svg']) {
  const svg = fs.readFileSync(path.join(BRAND_DIR, file), 'utf8');
  const viewBox = attr(svg, 'viewBox') || '0 0 23.16 17.04';
  const xml = convert(svg, viewBox);
  if (xml) { fs.writeFileSync(path.join(OUT_DIR, 'dsh_brand_fish.xml'), xml); made++; }
}

console.log('made=' + made);
if (skipped.length) console.log('skipped=' + skipped.join(', '));
