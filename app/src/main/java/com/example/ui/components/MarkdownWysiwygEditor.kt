package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmberNode
import com.example.ui.theme.ForestPrimary
import com.example.ui.theme.TerracottaAccent

@Composable
fun MarkdownWysiwygEditor(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Write or dictate your thoughts",
    minLines: Int = 8,
    onWikilinkClick: ((String) -> Unit)? = null
) {
    var isPreviewMode by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        // Mode Switcher & Formatting Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !isPreviewMode,
                    onClick = { isPreviewMode = false },
                    leadingIcon = {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                    },
                    label = { Text("Editor", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.testTag("markdown_edit_tab")
                )
                FilterChip(
                    selected = isPreviewMode,
                    onClick = { isPreviewMode = true },
                    leadingIcon = {
                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(14.dp))
                    },
                    label = { Text("WYSIWYG Preview", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.testTag("markdown_preview_tab")
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Formatting Toolbar (Visible in Edit Mode)
        if (!isPreviewMode) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ToolbarButton(icon = Icons.Default.FormatBold, label = "Bold") {
                        onValueChange(wrapOrAppend(value, "**", "**", "bold text"))
                    }
                    ToolbarButton(icon = Icons.Default.FormatItalic, label = "Italic") {
                        onValueChange(wrapOrAppend(value, "*", "*", "italic text"))
                    }
                    ToolbarButton(icon = Icons.Default.FormatStrikethrough, label = "Strike") {
                        onValueChange(wrapOrAppend(value, "~~", "~~", "strikethrough"))
                    }
                    ToolbarTextButton(text = "H1") {
                        onValueChange(appendPrefixToLine(value, "# "))
                    }
                    ToolbarTextButton(text = "H2") {
                        onValueChange(appendPrefixToLine(value, "## "))
                    }
                    ToolbarTextButton(text = "H3") {
                        onValueChange(appendPrefixToLine(value, "### "))
                    }
                    ToolbarButton(icon = Icons.Default.CheckBox, label = "Todo") {
                        onValueChange(appendPrefixToLine(value, "- [ ] "))
                    }
                    ToolbarButton(icon = Icons.Default.FormatListBulleted, label = "Bullets") {
                        onValueChange(appendPrefixToLine(value, "- "))
                    }
                    ToolbarButton(icon = Icons.Default.FormatListNumbered, label = "Numbers") {
                        onValueChange(appendPrefixToLine(value, "1. "))
                    }
                    ToolbarButton(icon = Icons.Default.FormatQuote, label = "Quote") {
                        onValueChange(appendPrefixToLine(value, "> "))
                    }
                    ToolbarButton(icon = Icons.Default.Code, label = "Code") {
                        onValueChange(wrapOrAppend(value, "`", "`", "code"))
                    }
                    ToolbarTextButton(text = "Callout") {
                        onValueChange(if (value.isBlank()) "> [!NOTE]\n> " else "$value\n\n> [!NOTE]\n> ")
                    }
                    ToolbarButton(icon = Icons.Default.Link, label = "Wikilink") {
                        onValueChange(wrapOrAppend(value, "[[", "]]", "Note"))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Body Text Field
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(label) },
                placeholder = { Text("Write freely in markdown... Support for **bold**, *italics*, #tags, [[wikilinks]], checkboxes, and > [!NOTE] callouts.") },
                textStyle = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
                minLines = minLines,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ForestPrimary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("entry_body_input")
            )
        } else {
            // Live WYSIWYG Rendered Preview
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("wysiwyg_preview_container")
            ) {
                SelectionContainer {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (value.isBlank()) {
                            Text(
                                text = "Preview is empty. Write something in the editor.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            RenderMarkdownBody(
                                markdown = value,
                                onCheckboxToggle = { lineIndex, isChecked ->
                                    val lines = value.lines().toMutableList()
                                    if (lineIndex in lines.indices) {
                                        val line = lines[lineIndex]
                                        lines[lineIndex] = if (isChecked) {
                                            line.replaceFirst("- [x]", "- [ ]").replaceFirst("- [X]", "- [ ]")
                                        } else {
                                            line.replaceFirst("- [ ]", "- [x]")
                                        }
                                        onValueChange(lines.joinToString("\n"))
                                    }
                                },
                                onWikilinkClick = onWikilinkClick
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolbarButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(34.dp).testTag("toolbar_$label")
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun ToolbarTextButton(
    text: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color.Transparent,
        modifier = Modifier
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .testTag("toolbar_$text")
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun RenderMarkdownBody(
    markdown: String,
    onCheckboxToggle: ((Int, Boolean) -> Unit)? = null,
    onWikilinkClick: ((String) -> Unit)? = null
) {
    val lines = markdown.lines()
    var inCallout = false
    var calloutType = "NOTE"
    val calloutLines = mutableListOf<String>()

    lines.forEachIndexed { index, line ->
        val trimmed = line.trim()

        // Check for Obsidian Callout syntax: > [!NOTE], > [!TIP], > [!WARNING], > [!IMPORTANT]
        if (trimmed.startsWith("> [!") && trimmed.endsWith("]")) {
            val type = trimmed.removePrefix("> [!").removeSuffix("]")
            calloutType = type
            inCallout = true
            calloutLines.clear()
            return@forEachIndexed
        }

        if (inCallout) {
            if (trimmed.startsWith(">")) {
                calloutLines.add(trimmed.removePrefix(">").trim())
                return@forEachIndexed
            } else {
                // Render accumulated callout box
                CalloutBox(type = calloutType, content = calloutLines.joinToString("\n"))
                inCallout = false
                calloutLines.clear()
            }
        }

        when {
            // Headings
            trimmed.startsWith("# ") -> {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = trimmed.removePrefix("# "),
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            trimmed.startsWith("## ") -> {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = trimmed.removePrefix("## "),
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                    color = ForestPrimary
                )
                Spacer(modifier = Modifier.height(3.dp))
            }
            trimmed.startsWith("### ") -> {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = trimmed.removePrefix("### "),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = TerracottaAccent
                )
                Spacer(modifier = Modifier.height(2.dp))
            }

            // Interactive Checkboxes
            trimmed.startsWith("- [ ] ") || trimmed.startsWith("- [x] ") || trimmed.startsWith("- [X] ") -> {
                val isChecked = trimmed.startsWith("- [x] ") || trimmed.startsWith("- [X] ")
                val itemText = trimmed.substring(6)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clickable(enabled = onCheckboxToggle != null) {
                            onCheckboxToggle?.invoke(index, isChecked)
                        }
                ) {
                    Icon(
                        imageVector = if (isChecked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                        contentDescription = null,
                        tint = if (isChecked) ForestPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = parseInlineFormatting(itemText),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            textDecoration = if (isChecked) TextDecoration.LineThrough else TextDecoration.None
                        ),
                        color = if (isChecked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Bullet Lists
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                Row(modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp)) {
                    Text("•", style = MaterialTheme.typography.bodyLarge, color = ForestPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = parseInlineFormatting(trimmed.substring(2)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Blockquotes
            trimmed.startsWith("> ") -> {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp),
                    border = androidx.compose.foundation.BorderStroke(width = 0.dp, color = Color.Transparent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(modifier = Modifier.padding(10.dp)) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(22.dp)
                                .background(ForestPrimary, RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = parseInlineFormatting(trimmed.removePrefix("> ")),
                            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Horizontal Divider
            trimmed == "---" || trimmed == "***" -> {
                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.material3.HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    modifier = Modifier.padding(vertical = 6.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Code Blocks
            trimmed.startsWith("```") -> {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Text(
                        text = trimmed.removePrefix("```").removeSuffix("```").trim(),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            // Empty line
            trimmed.isBlank() -> {
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Regular paragraph with inline wikilinks, bold, italic
            else -> {
                Text(
                    text = parseInlineFormatting(line),
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }

    if (inCallout && calloutLines.isNotEmpty()) {
        CalloutBox(type = calloutType, content = calloutLines.joinToString("\n"))
    }
}

@Composable
private fun CalloutBox(type: String, content: String) {
    val (icon, tint, bgColor) = when (type.uppercase()) {
        "WARNING" -> Triple(Icons.Default.Warning, TerracottaAccent, TerracottaAccent.copy(alpha = 0.1f))
        "TIP", "SUCCESS" -> Triple(Icons.Default.Info, ForestPrimary, ForestPrimary.copy(alpha = 0.1f))
        else -> Triple(Icons.Default.Info, AmberNode, AmberNode.copy(alpha = 0.1f))
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, tint.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = type.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = tint
                )
            }
            if (content.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

private fun parseInlineFormatting(text: String): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        val regex = Regex("(\\*\\*(.*?)\\*\\*)|(\\*([^*]+)\\*)|(~~(.*?)~~)|(`(.*?)`)|(\\[\\[(.*?)\\]\\])")
        val matches = regex.findAll(text)

        for (match in matches) {
            val range = match.range
            if (range.first > cursor) {
                append(text.substring(cursor, range.first))
            }

            val value = match.value
            when {
                // Bold: **text**
                value.startsWith("**") && value.endsWith("**") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(value.removeSurrounding("**"))
                    }
                }
                // Italic: *text*
                value.startsWith("*") && value.endsWith("*") && !value.startsWith("**") -> {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(value.removeSurrounding("*"))
                    }
                }
                // Strikethrough: ~~text~~
                value.startsWith("~~") && value.endsWith("~~") -> {
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        append(value.removeSurrounding("~~"))
                    }
                }
                // Inline Code: `code`
                value.startsWith("`") && value.endsWith("`") -> {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0x22888888))) {
                        append(value.removeSurrounding("`"))
                    }
                }
                // Wikilink: [[Note]]
                value.startsWith("[[") && value.endsWith("]]") -> {
                    val noteName = value.removeSurrounding("[[", "]]").split("|").last().split("#").first()
                    withStyle(SpanStyle(color = ForestPrimary, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline)) {
                        append("🔗 $noteName")
                    }
                }
                else -> append(value)
            }
            cursor = range.last + 1
        }

        if (cursor < text.length) {
            append(text.substring(cursor))
        }
    }
}

private fun wrapOrAppend(current: String, prefix: String, suffix: String, defaultPlaceholder: String): String {
    return if (current.isBlank()) {
        "$prefix$defaultPlaceholder$suffix"
    } else {
        "$current $prefix$defaultPlaceholder$suffix"
    }
}

private fun appendPrefixToLine(current: String, prefix: String): String {
    return if (current.isBlank()) {
        prefix
    } else {
        "$current\n$prefix"
    }
}
