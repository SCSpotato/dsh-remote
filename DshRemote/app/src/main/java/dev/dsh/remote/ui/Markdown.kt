package dev.dsh.remote.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.dsh.remote.ui.icons.DshIcon
import dev.dsh.remote.ui.icons.DshIcons
import dev.dsh.remote.ui.theme.DshGreen

private val C_KEYWORD = Color(0xFFC678DD)
private val C_STRING = Color(0xFF98C379)
private val C_COMMENT = Color(0xFF7F848E)
private val C_NUMBER = Color(0xFFD19A66)
private val C_LINK = Color(0xFF5EA1F0)

private val KEYWORDS = setOf(
    "if", "else", "for", "while", "return", "function", "fun", "class", "def", "import",
    "from", "as", "val", "var", "const", "let", "new", "try", "catch", "finally", "throw",
    "public", "private", "protected", "static", "void", "int", "long", "float", "double",
    "boolean", "bool", "char", "string", "str", "true", "false", "null", "nil", "None",
    "True", "False", "await", "async", "export", "default", "type", "interface", "enum",
    "switch", "case", "break", "continue", "extends", "implements", "this", "super", "and",
    "or", "not", "in", "is", "of", "with", "fn", "mut", "struct", "impl", "self", "goto",
)

/** Markdown renderer: code fences (highlighted + copy), headers, lists, links, tables, blockquotes, task lists. */
@Composable
fun MarkdownText(text: String, color: Color = MaterialTheme.colorScheme.onBackground) {
    val blocks = remember(text) { splitBlocks(text) }
    SelectionContainer {
        Column(Modifier.fillMaxWidth()) {
            for (block in blocks) {
                when (block) {
                    is MdCode -> CodeBlock(block.code, color)
                    is MdTable -> TableBlock(block, color)
                    is MdText -> {
                        for (line in block.lines) renderLine(line, color)
                    }
                }
            }
        }
    }
}

private sealed class MdBlock
private data class MdCode(val code: String) : MdBlock()
private data class MdTable(val header: List<String>, val rows: List<List<String>>) : MdBlock()
private data class MdText(val lines: List<String>) : MdBlock()

private fun splitBlocks(text: String): List<MdBlock> {
    val out = ArrayList<MdBlock>()
    val parts = text.split("```")
    for (i in parts.indices) {
        val part = parts[i]
        if (part.isBlank()) continue
        if (i % 2 == 1) {
            // code fence body; drop an optional leading language tag line
            val lines = part.lines()
            val code = if (lines.isNotEmpty() && !lines[0].trim().contains(' ')) lines.drop(1).joinToString("\n") else part.trimEnd('\n')
            out.add(MdCode(code.trimEnd('\n')))
        } else {
            // split out table blocks from ordinary text
            val lines = part.lines()
            var idx = 0
            val textLines = ArrayList<String>()
            while (idx < lines.size) {
                val line = lines[idx]
                if (line.trimStart().startsWith("|") && idx + 1 < lines.size &&
                    lines[idx + 1].trim().startsWith("|") && lines[idx + 1].contains("---")) {
                    if (textLines.isNotEmpty()) { out.add(MdText(textLines.toList())); textLines.clear() }
                    // table: header, separator, rows
                    val header = parseRow(line)
                    val rows = ArrayList<List<String>>()
                    idx += 2
                    while (idx < lines.size && lines[idx].trimStart().startsWith("|")) {
                        rows.add(parseRow(lines[idx]))
                        idx++
                    }
                    out.add(MdTable(header, rows))
                } else {
                    textLines.add(line)
                    idx++
                }
            }
            if (textLines.isNotEmpty()) out.add(MdText(textLines.toList()))
        }
    }
    return out
}

private fun parseRow(line: String): List<String> =
    line.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }

@Composable
private fun CodeBlock(code: String, color: Color) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
            .padding(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            IconButton(onClick = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("code", code))
                Toast.makeText(context, "已复制代码", Toast.LENGTH_SHORT).show()
            }, modifier = Modifier.width(32.dp)) {
                DshIcon(DshIcons.Copy, tint = MaterialTheme.colorScheme.onSurfaceVariant, size = 16.dp, contentDescription = "复制代码")
            }
        }
        Text(
            text = remember(code) { highlightCode(code, color) },
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun highlightCode(code: String, base: Color): androidx.compose.ui.text.AnnotatedString = buildAnnotatedString {
    var i = 0
    val n = code.length
    while (i < n) {
        val c = code[i]
        when {
            c == '"' || c == '\'' || c == '`' -> {
                val start = i
                i++
                while (i < n && code[i] != c) {
                    if (code[i] == '\\') i++
                    i++
                }
                if (i < n) i++
                withStyle(SpanStyle(color = C_STRING)) { append(code.substring(start, i)) }
            }
            c == '/' && i + 1 < n && code[i + 1] == '/' -> {
                val start = i
                while (i < n && code[i] != '\n') i++
                withStyle(SpanStyle(color = C_COMMENT)) { append(code.substring(start, i)) }
            }
            c == '/' && i + 1 < n && code[i + 1] == '*' -> {
                val start = i
                i += 2
                while (i + 1 < n && !(code[i] == '*' && code[i + 1] == '/')) i++
                if (i + 1 < n) i += 2
                withStyle(SpanStyle(color = C_COMMENT)) { append(code.substring(start, i)) }
            }
            c == '#' -> {
                val start = i
                while (i < n && code[i] != '\n') i++
                withStyle(SpanStyle(color = C_COMMENT)) { append(code.substring(start, i)) }
            }
            c.isDigit() -> {
                val start = i
                while (i < n && (code[i].isDigit() || code[i] == '.' || code[i] == 'x' ||
                        code[i] in 'a'..'f' || code[i] in 'A'..'F')) i++
                withStyle(SpanStyle(color = C_NUMBER)) { append(code.substring(start, i)) }
            }
            c.isLetter() || c == '_' -> {
                val start = i
                while (i < n && (code[i].isLetterOrDigit() || code[i] == '_')) i++
                val word = code.substring(start, i)
                if (word in KEYWORDS) {
                    withStyle(SpanStyle(color = C_KEYWORD, fontWeight = FontWeight.SemiBold)) { append(word) }
                } else {
                    append(word)
                }
            }
            else -> { append(c); i++ }
        }
    }
}

@Composable
private fun TableBlock(table: MdTable, color: Color) {
    val allRows = listOf(table.header) + table.rows
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .padding(8.dp),
    ) {
        allRows.forEachIndexed { rowIdx, row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { cell ->
                    Text(
                        cell,
                        color = color,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (rowIdx == 0) FontWeight.Bold else null,
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun renderLine(line: String, color: Color) {
    val trimmed = line.trimEnd()
    if (trimmed.isBlank()) return
    val base = MaterialTheme.typography.bodyLarge
    val content = trimmed.trimStart()
    val indent = trimmed.length - content.length
    val indentPad = (indent * 4).dp
    when {
        content.startsWith("#### ") -> InlineText(content.removePrefix("#### "), color, base.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp), Modifier.padding(top = 4.dp))
        content.startsWith("### ") -> InlineText(content.removePrefix("### "), color, base.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp), Modifier.padding(top = 4.dp))
        content.startsWith("## ") -> InlineText(content.removePrefix("## "), color, base.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp), Modifier.padding(top = 6.dp))
        content.startsWith("# ") -> InlineText(content.removePrefix("# "), color, base.copy(fontWeight = FontWeight.Bold, fontSize = 24.sp), Modifier.padding(top = 8.dp))
        content.startsWith("> ") -> InlineText(
            content.removePrefix("> "),
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.typography.bodySmall,
            Modifier.padding(start = 8.dp, top = 2.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(4.dp)).padding(6.dp),
        )
        content.startsWith("- [ ] ") || content.startsWith("* [ ] ") -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = indentPad)) {
            Box(Modifier.size(12.dp).border(1.dp, color, RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(4.dp))
            InlineText(content.removePrefix("- [ ] ").removePrefix("* [ ] "), color, base, Modifier.weight(1f))
        }
        content.startsWith("- [x] ") || content.startsWith("* [x] ") -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = indentPad)) {
            DshIcon(DshIcons.CheckSmall, tint = DshGreen, size = 12.dp)
            Spacer(Modifier.width(4.dp))
            InlineText(content.removePrefix("- [x] ").removePrefix("* [x] "), color, base, Modifier.weight(1f))
        }
        content.startsWith("- ") || content.startsWith("* ") -> {
            // Word-style multilevel bullets: level 1 solid circle, level 2+ hollow circle.
            val bullet = if (indent == 0) "●  " else "○  "
            Row(Modifier.padding(start = indentPad)) {
                Text(bullet, color = color)
                InlineText(content.removePrefix("- ").removePrefix("* "), color, base, Modifier.weight(1f))
            }
        }
        else -> InlineText(content, color, base)
    }
}

@Composable
private fun InlineText(text: String, color: Color, style: TextStyle, modifier: Modifier = Modifier) {
    // Inline code renders as a clearly-visible grey box; the background follows
    // text wrapping (so long paths wrap to the next line instead of overflowing).
    val codeBg = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
    val annotated = remember(text, codeBg) {
        buildAnnotatedString {
            val boldRe = Regex("\\*\\*(.+?)\\*\\*")
            val codeRe = Regex("`([^`]+)`")
            val linkRe = Regex("\\[([^\\]]+)\\]\\(([^)\\s]+)\\)")
            val imageRe = Regex("!\\[([^\\]]*)\\]\\(([^)\\s]+)\\)")
            var i = 0
            while (i < text.length) {
                val candidates = listOfNotNull(
                    boldRe.find(text, i)?.let { it to 0 },
                    codeRe.find(text, i)?.let { it to 1 },
                    linkRe.find(text, i)?.let { it to 2 },
                    imageRe.find(text, i)?.let { it to 3 },
                )
                if (candidates.isEmpty()) { append(text.substring(i)); break }
                val (m, kind) = candidates.minBy { it.first.range.first }
                if (m.range.first > i) append(text.substring(i, m.range.first))
                when (kind) {
                    0 -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(m.groupValues[1]) }
                    1 -> withStyle(SpanStyle(background = codeBg)) { append(m.groupValues[1]) }
                    2 -> withLink(LinkAnnotation.Url(m.groupValues[2])) { withStyle(SpanStyle(color = C_LINK)) { append(m.groupValues[1]) } }
                    3 -> withLink(LinkAnnotation.Url(m.groupValues[2])) { withStyle(SpanStyle(color = C_LINK)) { append(m.groupValues[1].ifBlank { "图片" }) } }
                }
                i = m.range.last + 1
            }
        }
    }
    Text(text = annotated, color = color, style = style, modifier = modifier)
}
