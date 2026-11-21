package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Palette used by the markdown renderer. Colors are resolved from the theme
 * in the composable layer and injected into the transformation, since
 * VisualTransformation cannot read CompositionLocals.
 */
data class MdPalette(
    val baseFontSize: TextUnit = 16.sp,
    val headingColor: Color = Color.Unspecified,
    val quoteColor: Color = Color.Unspecified,
    val codeColor: Color = Color.Unspecified,
    val codeBackground: Color = Color.Unspecified,
    val linkColor: Color = Color.Unspecified,
    val highlightBackground: Color = Color.Unspecified,
    val calloutBackground: Color = Color.Unspecified,
    val dividerColor: Color = Color.Unspecified
)

enum class MdBlockAction {
    H1,
    H2,
    H3,
    TODO,
    BULLET,
    NUMBERED,
    QUOTE,
    CALLOUT,
    CODE,
    DIVIDER,
    WIKILINK,
    BOLD,
    ITALIC,
    STRIKE
}

data class SlashTrigger(
    val slashStart: Int,
    val cursorEnd: Int,
    val query: String
)

fun detectSlashCommandQuery(value: TextFieldValue): SlashTrigger? {
    val sel = value.selection
    if (!sel.collapsed) return null
    val cursor = sel.end.coerceIn(0, value.text.length)
    val lineStart = if (cursor == 0) 0 else value.text.lastIndexOf('\n', cursor - 1) + 1
    val beforeCursor = value.text.substring(lineStart, cursor)
    val slashIdxInLine = beforeCursor.lastIndexOf('/')
    if (slashIdxInLine < 0) return null
    if (slashIdxInLine > 0 && !beforeCursor[slashIdxInLine - 1].isWhitespace()) return null
    val query = beforeCursor.substring(slashIdxInLine + 1)
    if (query.contains(' ') || query.length > 24) return null
    return SlashTrigger(
        slashStart = lineStart + slashIdxInLine,
        cursorEnd = cursor,
        query = query
    )
}

fun replaceSlashTriggerWith(
    value: TextFieldValue,
    trigger: SlashTrigger,
    insertion: String
): TextFieldValue {
    val safeStart = trigger.slashStart.coerceIn(0, value.text.length)
    val safeEnd = trigger.cursorEnd.coerceIn(safeStart, value.text.length)
    val newText = value.text.substring(0, safeStart) + insertion + value.text.substring(safeEnd)
    val caret = (safeStart + insertion.length).coerceAtMost(newText.length)
    return TextFieldValue(text = newText, selection = TextRange(caret))
}

/** One piece of the output: where it comes from, what it renders as. */
internal data class MdSegment(
    val origStart: Int,
    val origEnd: Int,
    val output: String,
    val spans: List<SpanStyle> = emptyList(),
    /** Wikilink target (the text inside [[…]]), when this segment is one. */
    val wikilink: String? = null
)

/**
 * Single-pass markdown segmenter shared by the live editor and the read-only
 * renderer. Supports headings, callouts, quotes, dividers, bullets, numbered
 * lists, task checkboxes and inline formatting.
 */
internal class MarkdownParser(private val palette: MdPalette) {

    private val boldStar = Regex("\\*\\*([^*\\n]+)\\*\\*")
    private val boldUnder = Regex("(?<!\\w)__([^_\\n]+)__(?!\\w)")
    private val italicStar = Regex("(?<!\\*)\\*([^*\\n]+)\\*(?!\\*)")
    private val italicUnder = Regex("(?<!\\w)_([^_\\n]+)_(?!\\w)")
    private val strike = Regex("~~([^~\\n]+)~~")
    private val code = Regex("`([^`\\n]+)`")
    private val wikilink = Regex("\\[\\[([^\\[\\]\\n]+)\\]\\]")
    private val highlight = Regex("==([^=\\n]+)==")

    private val headingRegex = Regex("^(#{1,6})\\s+")
    private val dividerRegex = Regex("^\\s*(---|\\*\\*\\*|___)\\s*$")
    private val taskRegex = Regex("^[-*+]\\s+\\[([ xX])\\]\\s+")
    private val bulletRegex = Regex("^[-*+]\\s+")
    private val calloutRegex = Regex("^(?:>\\s*\\[!\\w+\\]\\s*|💡\\s+|>\\s*💡\\s+)")
    private val quoteRegex = Regex("^(>\\s?)+")

    fun parse(text: String): List<MdSegment> {
        val segments = mutableListOf<MdSegment>()
        var i = 0
        while (i <= text.length) {
            if (i == text.length) break
            val lineEnd = text.indexOf('\n', i).let { if (it == -1) text.length else it }
            if (lineEnd > i) {
                parseLine(text.substring(i, lineEnd), i, segments)
            } else {
                segments.add(MdSegment(i, i, ""))
            }
            if (lineEnd < text.length) {
                segments.add(MdSegment(lineEnd, lineEnd + 1, "\n"))
            }
            i = lineEnd + 1
        }
        return mergePlain(segments)
    }

    private fun mergePlain(segments: List<MdSegment>): List<MdSegment> {
        val merged = mutableListOf<MdSegment>()
        for (seg in segments) {
            val last = merged.lastOrNull()
            if (last != null && last.spans.isEmpty() && seg.spans.isEmpty() &&
                last.origEnd == seg.origStart
            ) {
                merged[merged.size - 1] = last.copy(output = last.output + seg.output)
            } else {
                merged.add(seg)
            }
        }
        return merged
    }

    private fun parseLine(line: String, base: Int, segments: MutableList<MdSegment>) {
        if (dividerRegex.matches(line)) {
            segments.add(
                MdSegment(
                    origStart = base,
                    origEnd = base + line.length,
                    output = "────────────────────",
                    spans = listOf(
                        SpanStyle(
                            color = palette.dividerColor.takeIf { it != Color.Unspecified }
                                ?: palette.quoteColor.copy(alpha = 0.45f)
                        )
                    )
                )
            )
            return
        }

        var cursor = 0
        val headingMatch = headingRegex.find(line)
        val taskMatch = taskRegex.find(line)
        val bulletMatch = bulletRegex.find(line)
        val calloutMatch = calloutRegex.find(line)
        val quoteMatch = quoteRegex.find(line)

        var blockSpans: List<SpanStyle> = emptyList()

        when {
            headingMatch != null -> {
                val level = headingMatch.groupValues[1].length
                val factor = when (level) {
                    1 -> 1.48f
                    2 -> 1.28f
                    3 -> 1.15f
                    else -> 1.06f
                }
                blockSpans = listOf(
                    SpanStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = palette.baseFontSize * factor,
                        color = palette.headingColor
                    )
                )
                segments.add(MdSegment(base, base + headingMatch.value.length, ""))
                cursor = headingMatch.value.length
            }
            taskMatch != null -> {
                val done = taskMatch.groupValues[1].equals("x", true)
                val box = if (done) "☑" else "☐"
                segments.add(
                    MdSegment(
                        base,
                        base + taskMatch.value.length,
                        "$box ",
                        if (done) listOf(
                            SpanStyle(
                                color = palette.linkColor,
                                fontWeight = FontWeight.Medium
                            )
                        ) else listOf(
                            SpanStyle(
                                color = palette.quoteColor
                            )
                        )
                    )
                )
                if (done) {
                    blockSpans = listOf(
                        SpanStyle(
                            color = palette.quoteColor,
                            textDecoration = TextDecoration.LineThrough
                        )
                    )
                }
                cursor = taskMatch.value.length
            }
            bulletMatch != null -> {
                segments.add(MdSegment(base, base + bulletMatch.value.length, "•  "))
                cursor = bulletMatch.value.length
            }
            calloutMatch != null -> {
                val calloutBg = palette.calloutBackground.takeIf { it != Color.Unspecified }
                    ?: palette.codeBackground
                blockSpans = listOf(
                    SpanStyle(
                        background = calloutBg,
                        fontWeight = FontWeight.Medium
                    )
                )
                segments.add(
                    MdSegment(
                        base,
                        base + calloutMatch.value.length,
                        "💡 ",
                        listOf(SpanStyle(background = calloutBg))
                    )
                )
                cursor = calloutMatch.value.length
            }
            quoteMatch != null -> {
                blockSpans = listOf(
                    SpanStyle(
                        color = palette.quoteColor,
                        fontStyle = FontStyle.Italic
                    )
                )
                segments.add(
                    MdSegment(
                        base,
                        base + quoteMatch.value.length,
                        "│ ",
                        listOf(SpanStyle(color = palette.linkColor, fontWeight = FontWeight.Bold))
                    )
                )
                cursor = quoteMatch.value.length
            }
        }

        parseInline(line, cursor, base, blockSpans, segments)
    }

    private fun parseInline(
        line: String,
        from: Int,
        base: Int,
        blockSpans: List<SpanStyle>,
        segments: MutableList<MdSegment>
    ) {
        data class MatchInfo(
            val start: Int,
            val end: Int,
            val contentStart: Int,
            val contentEnd: Int,
            val spans: List<SpanStyle>,
            val link: String? = null
        )

        val candidates = mutableListOf<MatchInfo>()

        fun collect(
            regex: Regex,
            linkBuilder: ((String) -> String?)? = null,
            spansBuilder: (String) -> List<SpanStyle>
        ) {
            regex.findAll(line).forEach { match ->
                if (match.range.first < from) return@forEach
                val contentGroup = match.groups[1]
                val contentStart = contentGroup?.range?.first ?: match.range.first
                val contentEnd = (contentGroup?.range?.last ?: match.range.last) + 1
                candidates.add(
                    MatchInfo(
                        start = match.range.first,
                        end = match.range.last + 1,
                        contentStart = contentStart,
                        contentEnd = contentEnd,
                        spans = spansBuilder(match.value),
                        link = linkBuilder?.invoke(line.substring(contentStart, contentEnd))
                    )
                )
            }
        }

        collect(boldStar) { listOf(SpanStyle(fontWeight = FontWeight.Bold)) }
        collect(boldUnder) { listOf(SpanStyle(fontWeight = FontWeight.Bold)) }
        collect(italicStar) { listOf(SpanStyle(fontStyle = FontStyle.Italic)) }
        collect(italicUnder) { listOf(SpanStyle(fontStyle = FontStyle.Italic)) }
        collect(strike) {
            listOf(
                SpanStyle(
                    color = palette.quoteColor,
                    textDecoration = TextDecoration.LineThrough
                )
            )
        }
        collect(code) {
            listOf(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    color = palette.codeColor,
                    background = palette.codeBackground,
                    fontSize = palette.baseFontSize * 0.92f
                )
            )
        }
        collect(wikilink, linkBuilder = { it }) {
            listOf(
                SpanStyle(
                    color = palette.linkColor,
                    fontWeight = FontWeight.Medium,
                    textDecoration = TextDecoration.Underline
                )
            )
        }
        collect(highlight) { listOf(SpanStyle(background = palette.highlightBackground)) }

        val accepted = mutableListOf<MatchInfo>()
        candidates.sortedBy { it.start }.forEach { candidate ->
            val conflicts = accepted.any { acc ->
                candidate.start < acc.end && candidate.end > acc.start
            }
            if (!conflicts) accepted.add(candidate)
        }

        var cursor = from
        accepted.forEach { match ->
            if (match.start > cursor) {
                segments.add(
                    MdSegment(
                        base + cursor,
                        base + match.start,
                        line.substring(cursor, match.start),
                        blockSpans
                    )
                )
            }
            if (match.contentStart > match.start) {
                segments.add(MdSegment(base + match.start, base + match.contentStart, ""))
            }
            segments.add(
                MdSegment(
                    base + match.contentStart,
                    base + match.contentEnd,
                    line.substring(match.contentStart, match.contentEnd),
                    blockSpans + match.spans,
                    match.link
                )
            )
            if (match.end > match.contentEnd) {
                segments.add(MdSegment(base + match.contentEnd, base + match.end, ""))
            }
            cursor = match.end
        }

        if (cursor < line.length) {
            segments.add(
                MdSegment(
                    base + cursor,
                    base + line.length,
                    line.substring(cursor, line.length),
                    blockSpans
                )
            )
        }
    }
}

class MarkdownVisualTransformation(private val palette: MdPalette) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val segments = MarkdownParser(palette).parse(text.text)
        val builder = AnnotatedString.Builder()
        segments.forEach { seg ->
            if (seg.output.isEmpty()) return@forEach
            if (seg.spans.isEmpty()) {
                builder.append(seg.output)
            } else {
                seg.spans.forEach { builder.pushStyle(it) }
                builder.append(seg.output)
                repeat(seg.spans.size) { builder.pop() }
            }
        }
        return TransformedText(builder.toAnnotatedString(), MdOffsetMapping(segments))
    }
}

internal class MdOffsetMapping(private val segments: List<MdSegment>) : OffsetMapping {

    private val outStarts = IntArray(segments.size)
    private val outEnds = IntArray(segments.size)

    init {
        var acc = 0
        segments.forEachIndexed { idx, seg ->
            outStarts[idx] = acc
            acc += seg.output.length
            outEnds[idx] = acc
        }
    }

    private val totalOriginal: Int get() = segments.lastOrNull()?.origEnd ?: 0
    private val totalTransformed: Int get() = outEnds.lastOrNull() ?: 0

    override fun originalToTransformed(offset: Int): Int {
        if (segments.isEmpty() || offset <= 0) return 0
        if (offset >= totalOriginal) return totalTransformed
        val idx = findIndexByOrig(offset)
        val seg = segments[idx]
        if (offset >= seg.origEnd) return outEnds[idx]
        return interpolate(offset, seg.origStart, seg.origEnd, outStarts[idx], outEnds[idx])
    }

    override fun transformedToOriginal(offset: Int): Int {
        if (segments.isEmpty() || offset <= 0) return 0
        if (offset >= totalTransformed) return totalOriginal
        val idx = findIndexByOut(offset)
        val seg = segments[idx]
        if (offset >= outEnds[idx]) return seg.origEnd
        val outLen = outEnds[idx] - outStarts[idx]
        if (outLen <= 0) return seg.origStart
        val origLen = seg.origEnd - seg.origStart
        val delta = offset - outStarts[idx]
        return seg.origStart + Math.round(delta * origLen.toFloat() / outLen)
    }

    private fun interpolate(
        offset: Int,
        origStart: Int,
        origEnd: Int,
        outStart: Int,
        outEnd: Int
    ): Int {
        val origLen = origEnd - origStart
        if (origLen <= 0) return outEnd
        val outLen = outEnd - outStart
        if (outLen <= 0) return outStart
        val delta = offset - origStart
        return outStart + Math.round(delta * outLen.toFloat() / origLen)
    }

    private fun findIndexByOrig(offset: Int): Int {
        var lo = 0
        var hi = segments.size - 1
        var result = 0
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (segments[mid].origStart <= offset) {
                result = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return result
    }

    private fun findIndexByOut(offset: Int): Int {
        var lo = 0
        var hi = segments.size - 1
        var result = 0
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (outStarts[mid] <= offset) {
                result = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return result
    }
}

@Composable
fun rememberMdPalette(): MdPalette {
    val scheme = MaterialTheme.colorScheme
    return remember(scheme) {
        MdPalette(
            baseFontSize = 16.sp,
            headingColor = scheme.onSurface,
            quoteColor = scheme.onSurfaceVariant,
            codeColor = scheme.tertiary,
            codeBackground = scheme.surfaceVariant.copy(alpha = 0.72f),
            linkColor = scheme.primary,
            highlightBackground = Color(0x55FFD54F),
            calloutBackground = scheme.primary.copy(alpha = 0.08f),
            dividerColor = scheme.outline.copy(alpha = 0.45f)
        )
    }
}

@Composable
fun LiveMarkdownEditor(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Escribe o pulsa «/» para añadir bloques…",
    showToolbar: Boolean = true,
    externalFieldValue: TextFieldValue? = null,
    onTextFieldValueChange: ((TextFieldValue) -> Unit)? = null,
    onWikilinkClick: ((String) -> Unit)? = null
) {
    val palette = rememberMdPalette()
    val transformation = remember(palette) { MarkdownVisualTransformation(palette) }

    var internalFieldValue by remember { mutableStateOf(TextFieldValue(value)) }
    val activeFieldValue = externalFieldValue ?: internalFieldValue

    LaunchedEffect(value) {
        if (externalFieldValue == null && value != internalFieldValue.text) {
            val sel = internalFieldValue.selection
            val clamped = TextRange(
                sel.min.coerceAtMost(value.length),
                sel.max.coerceAtMost(value.length)
            )
            internalFieldValue = internalFieldValue.copy(text = value, selection = clamped)
        }
    }

    val textStyle = MaterialTheme.typography.bodyLarge.copy(
        color = MaterialTheme.colorScheme.onSurface,
        lineHeight = 27.sp,
        fontSize = 16.sp
    )

    Column(modifier = modifier.fillMaxWidth()) {
        if (showToolbar) {
            MarkdownToolbar(
                onAction = { action ->
                    val next = applyMdBlockAction(action, activeFieldValue)
                    if (externalFieldValue == null) internalFieldValue = next
                    onTextFieldValueChange?.invoke(next)
                    onValueChange(next.text)
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        BasicTextField(
            value = activeFieldValue,
            onValueChange = { next ->
                if (externalFieldValue == null) internalFieldValue = next
                onTextFieldValueChange?.invoke(next)
                onValueChange(next.text)
            },
            visualTransformation = transformation,
            textStyle = textStyle,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 240.dp),
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (activeFieldValue.text.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = textStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f))
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}

private fun wrapSelection(
    value: TextFieldValue,
    marker: String,
    endMarker: String = marker
): TextFieldValue {
    val text = value.text
    val start = value.selection.min
    val end = value.selection.max
    val selected = text.substring(start, end)

    if (start >= marker.length && end <= text.length - endMarker.length) {
        val before = text.substring(0, start)
        val after = text.substring(end)
        if (before.endsWith(marker) && after.startsWith(endMarker)) {
            val newText = before.removeSuffix(marker) + selected + after.removePrefix(endMarker)
            return TextFieldValue(
                text = newText,
                selection = TextRange((start - marker.length).coerceAtLeast(0))
            )
        }
    }

    val newText = text.substring(0, start) + marker + selected + endMarker + text.substring(end)
    val selStart = start + marker.length
    return TextFieldValue(
        text = newText,
        selection = TextRange(selStart, selStart + selected.length)
    )
}

private fun toggleLinePrefix(
    value: TextFieldValue,
    prefix: String
): TextFieldValue {
    val text = value.text
    val anchor = value.selection.min.coerceIn(0, text.length)
    val lineStart = if (anchor == 0) 0 else text.lastIndexOf('\n', anchor - 1) + 1
    val lineEndRaw = text.indexOf('\n', anchor).let { if (it == -1) text.length else it }
    val line = text.substring(lineStart, lineEndRaw)

    val next: String = if (line.startsWith(prefix)) {
        line.removePrefix(prefix)
    } else {
        val cleaned = line.replaceFirst(Regex("^(#{1,6}\\s+|[-*+>]\\s*(\\[[ xX]\\]\\s*)?|\\d+\\.\\s+|💡\\s+)"), "")
        prefix + cleaned
    }

    val newText = text.substring(0, lineStart) + next + text.substring(lineEndRaw)
    val caret = (lineStart + next.length).coerceAtMost(newText.length)
    return TextFieldValue(text = newText, selection = TextRange(caret))
}

private fun insertDividerLine(value: TextFieldValue): TextFieldValue {
    val text = value.text
    val cursor = value.selection.max.coerceIn(0, text.length)
    val needLeadingNewline = cursor > 0 && text[cursor - 1] != '\n'
    val snippet = (if (needLeadingNewline) "\n" else "") + "---\n"
    val newText = text.substring(0, cursor) + snippet + text.substring(cursor)
    val caret = (cursor + snippet.length).coerceAtMost(newText.length)
    return TextFieldValue(text = newText, selection = TextRange(caret))
}

fun applyMdBlockAction(action: MdBlockAction, current: TextFieldValue): TextFieldValue {
    return when (action) {
        MdBlockAction.BOLD -> wrapSelection(current, "**")
        MdBlockAction.ITALIC -> wrapSelection(current, "*")
        MdBlockAction.STRIKE -> wrapSelection(current, "~~")
        MdBlockAction.CODE -> wrapSelection(current, "`")
        MdBlockAction.WIKILINK -> wrapSelection(current, "[[", "]]")
        MdBlockAction.H1 -> toggleLinePrefix(current, "# ")
        MdBlockAction.H2 -> toggleLinePrefix(current, "## ")
        MdBlockAction.H3 -> toggleLinePrefix(current, "### ")
        MdBlockAction.QUOTE -> toggleLinePrefix(current, "> ")
        MdBlockAction.CALLOUT -> toggleLinePrefix(current, "💡 ")
        MdBlockAction.BULLET -> toggleLinePrefix(current, "- ")
        MdBlockAction.NUMBERED -> toggleLinePrefix(current, "1. ")
        MdBlockAction.TODO -> toggleLinePrefix(current, "- [ ] ")
        MdBlockAction.DIVIDER -> insertDividerLine(current)
    }
}

@Composable
private fun MarkdownToolbar(onAction: (MdBlockAction) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolbarIcon(Icons.Default.FormatBold, "Negrita") { onAction(MdBlockAction.BOLD) }
            ToolbarIcon(Icons.Default.FormatItalic, "Cursiva") { onAction(MdBlockAction.ITALIC) }
            ToolbarIcon(Icons.Default.FormatStrikethrough, "Tachado") { onAction(MdBlockAction.STRIKE) }
            ToolbarIcon(Icons.Default.Code, "Código") { onAction(MdBlockAction.CODE) }
            ToolbarText("H1") { onAction(MdBlockAction.H1) }
            ToolbarText("H2") { onAction(MdBlockAction.H2) }
            ToolbarText("H3") { onAction(MdBlockAction.H3) }
            ToolbarIcon(Icons.Default.CheckBoxOutlineBlank, "Tarea") { onAction(MdBlockAction.TODO) }
            ToolbarIcon(Icons.Default.FormatListBulleted, "Lista") { onAction(MdBlockAction.BULLET) }
            ToolbarIcon(Icons.Default.FormatListNumbered, "Numerada") { onAction(MdBlockAction.NUMBERED) }
            ToolbarIcon(Icons.Default.Lightbulb, "Callout") { onAction(MdBlockAction.CALLOUT) }
            ToolbarIcon(Icons.Default.FormatQuote, "Cita") { onAction(MdBlockAction.QUOTE) }
            ToolbarIcon(Icons.Default.HorizontalRule, "Divisor") { onAction(MdBlockAction.DIVIDER) }
            ToolbarIcon(Icons.Default.Link, "Enlace wiki") { onAction(MdBlockAction.WIKILINK) }
        }
    }
}

@Composable
private fun ToolbarIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(19.dp)
        )
    }
}

@Composable
private fun ToolbarText(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    onWikilinkClick: ((String) -> Unit)? = null
) {
    val palette = rememberMdPalette()
    val segments = remember(text, palette) { MarkdownParser(palette).parse(text) }

    val annotated = remember(text, palette) {
        val builder = AnnotatedString.Builder()
        segments.forEach { seg ->
            if (seg.output.isEmpty()) return@forEach
            val link = if (onWikilinkClick != null) seg.wikilink else null
            if (link != null) {
                builder.pushStringAnnotation(tag = "MD_WIKILINK", annotation = link)
                builder.pushStyle(
                    SpanStyle(
                        color = palette.linkColor,
                        fontWeight = FontWeight.Medium,
                        textDecoration = TextDecoration.Underline
                    )
                )
                builder.append(seg.output)
                builder.pop()
                builder.pop()
            } else if (seg.spans.isEmpty()) {
                builder.append(seg.output)
            } else {
                seg.spans.forEach { builder.pushStyle(it) }
                builder.append(seg.output)
                repeat(seg.spans.size) { builder.pop() }
            }
        }
        builder.toAnnotatedString()
    }

    if (onWikilinkClick != null) {
        ClickableText(
            text = annotated,
            style = style.copy(
                color = style.color.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.onSurface
            ),
            modifier = modifier,
            onClick = { offset ->
                annotated.getStringAnnotations(tag = "MD_WIKILINK", start = offset, end = offset)
                    .firstOrNull()?.let { onWikilinkClick(it.item) }
            }
        )
    } else {
        Text(
            text = annotated,
            style = style.copy(color = style.color.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.onSurface),
            modifier = modifier
        )
    }
}
