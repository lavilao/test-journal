package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ForestPrimary
import com.example.ui.theme.TerracottaAccent

data class MoodEmoji(
    val emoji: String,
    val name: String,
    val category: String = "general"
)

val STORYPAD_MOOD_EMOJIS = listOf(
    MoodEmoji("😊", "Joyful"),
    MoodEmoji("😌", "Peaceful"),
    MoodEmoji("💡", "Inspired"),
    MoodEmoji("🎯", "Focused"),
    MoodEmoji("💭", "Thoughtful"),
    MoodEmoji("🌿", "Calm"),
    MoodEmoji("☕", "Productive"),
    MoodEmoji("❤️", "Grateful"),
    MoodEmoji("🔥", "Energized"),
    MoodEmoji("🌧️", "Melancholy"),
    MoodEmoji("😴", "Tired"),
    MoodEmoji("🧘", "Mindful"),
    MoodEmoji("✨", "Wonder"),
    MoodEmoji("🚀", "Ambitious")
)

@Composable
fun EmojiMoodPicker(
    selectedMood: String?,
    onMoodSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCustomDialog by remember { mutableStateOf(false) }
    var customEmojiInput by remember { mutableStateOf("") }
    var customLabelInput by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Mood & Emotion",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (selectedMood != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = ForestPrimary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = selectedMood,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = ForestPrimary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear mood",
                                tint = ForestPrimary,
                                modifier = Modifier
                                    .size(12.dp)
                                    .clickable { onMoodSelected(null) }
                            )
                        }
                    }
                }
            }

            TextButton(
                onClick = { showCustomDialog = true },
                modifier = Modifier.testTag("custom_emoji_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(2.dp))
                Text("Custom", style = MaterialTheme.typography.labelSmall)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Horizontal Storypad-Style Scrolling Emoji Carousel
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            STORYPAD_MOOD_EMOJIS.forEach { item ->
                val fullLabel = "${item.emoji} ${item.name}"
                val isSelected = selectedMood == fullLabel || selectedMood == item.name || selectedMood?.startsWith(item.emoji) == true

                val scale by animateFloatAsState(targetValue = if (isSelected) 1.08f else 1f, label = "scale")
                val borderColor by animateColorAsState(
                    targetValue = if (isSelected) ForestPrimary else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                    label = "border"
                )
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) ForestPrimary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                    label = "bg"
                )

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = bgColor,
                    border = androidx.compose.foundation.BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor),
                    modifier = Modifier
                        .scale(scale)
                        .clickable {
                            if (isSelected) {
                                onMoodSelected(null)
                            } else {
                                onMoodSelected(fullLabel)
                            }
                        }
                        .testTag("emoji_mood_${item.name}")
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = item.emoji,
                            fontSize = 24.sp,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (isSelected) ForestPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showCustomDialog) {
        AlertDialog(
            onDismissRequest = { showCustomDialog = false },
            title = { Text("Custom Emoji Mood") },
            text = {
                Column {
                    OutlinedTextField(
                        value = customEmojiInput,
                        onValueChange = { customEmojiInput = it },
                        label = { Text("Emoji (e.g. 🎨, 🏕️, ⚡)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customLabelInput,
                        onValueChange = { customLabelInput = it },
                        label = { Text("Feeling Name (e.g. Creative, Adventurous)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val emoji = customEmojiInput.trim()
                        val label = customLabelInput.trim()
                        if (emoji.isNotBlank()) {
                            val combined = if (label.isNotBlank()) "$emoji $label" else emoji
                            onMoodSelected(combined)
                            showCustomDialog = false
                            customEmojiInput = ""
                            customLabelInput = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ForestPrimary)
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
