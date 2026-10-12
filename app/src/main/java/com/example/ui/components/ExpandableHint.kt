package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * v1.7: settings screens used to dump 3-line explanations on every option.
 * This composable shows ONE short line; the full text unfolds on tap.
 * If the text is already short (≤ 72 chars) it renders plain, no toggle.
 */
@Composable
fun ExpandableHint(
    text: String,
    modifier: Modifier = Modifier,
    maxShortChars: Int = 72,
    style: TextStyle? = null,
    color: Color? = null
) {
    val textStyle = style ?: MaterialTheme.typography.bodySmall
    val textColor = color ?: MaterialTheme.colorScheme.onSurfaceVariant
    val isShort = text.length <= maxShortChars
    val short = remember(text) { firstSentence(text, maxShortChars) }
    var expanded by rememberSaveable(text) { mutableStateOf(false) }

    if (isShort) {
        Text(
            text = text,
            style = textStyle,
            color = textColor,
            modifier = modifier
        )
        return
    }

    Column(modifier = modifier) {
        Text(
            text = if (expanded) text else short,
            style = textStyle,
            color = textColor
        )
        Text(
            text = if (expanded) "Menos" else "Más",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 2.dp)
                .clickable { expanded = !expanded }
                .padding(horizontal = 2.dp)
        )
    }
}

/** First sentence (or first `maxChars` chars), ellipsized when truncated. */
private fun firstSentence(text: String, maxChars: Int): String {
    val trimmed = text.trim()
    val sentenceEnd = trimmed.indexOfAny(charArrayOf('.', '\n'))
    val candidate = if (sentenceEnd > 0) trimmed.substring(0, sentenceEnd) else trimmed
    return if (candidate.length <= maxChars && sentenceEnd > 0) {
        candidate
    } else {
        val cut = trimmed.take(maxChars)
        val lastSpace = cut.lastIndexOf(' ')
        (if (lastSpace > maxChars / 2) cut.substring(0, lastSpace) else cut).trimEnd() + "…"
    }
}
