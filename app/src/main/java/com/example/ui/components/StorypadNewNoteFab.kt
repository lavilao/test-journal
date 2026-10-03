package com.example.ui.components

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmberNode
import com.example.ui.theme.ForestPrimary
import com.example.ui.theme.TerracottaAccent

enum class StorypadNoteType(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val color: Color
) {
    TEXT("Text Note", "Classic markdown reflection", Icons.Default.Edit, ForestPrimary),
    AUDIO("Voice Note", "Voice journal with speech-to-text", Icons.Default.Mic, TerracottaAccent),
    IMAGE("Photo Note", "Visual memory with scene & face detection", Icons.Default.PhotoCamera, Color(0xFF2D6A4F)),
    TEMPLATE("Guided Template", "Structured prompts & gratitude", Icons.Default.DashboardCustomize, AmberNode),
    QUICK("Quick Thought", "Fleeting thought & rapid capture", Icons.Default.FlashOn, Color(0xFF7B2CBF))
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun StorypadNewNoteFab(
    onTriggerType: (StorypadNoteType) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("storypad_note_prefs", Context.MODE_PRIVATE) }
    var currentType by remember {
        val savedName = prefs.getString("last_note_type", StorypadNoteType.TEXT.name)
        mutableStateOf(
            try {
                StorypadNoteType.valueOf(savedName ?: StorypadNoteType.TEXT.name)
            } catch (_: Exception) {
                StorypadNoteType.TEXT
            }
        )
    }

    var showMenuSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End
    ) {
        // Storypad FAB: short tap creates currentType; on hold shows menu
        Surface(
            shape = CircleShape,
            color = currentType.color,
            shadowElevation = 6.dp,
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .combinedClickable(
                    onClick = {
                        onTriggerType(currentType)
                    },
                    onLongClick = {
                        showMenuSheet = true
                    }
                )
                .testTag("storypad_fab")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = currentType.icon,
                    contentDescription = "New ${currentType.title} (Hold to change)",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }

    if (showMenuSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMenuSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .testTag("storypad_note_type_menu")
            ) {
                Text(
                    text = "Create New Memory",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Select note type. Your choice will become the default on the FAB.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                StorypadNoteType.values().forEach { type ->
                    val isSelected = currentType == type
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) type.color.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) type.color else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .combinedClickable(
                                onClick = {
                                    currentType = type
                                    prefs.edit().putString("last_note_type", type.name).apply()
                                    showMenuSheet = false
                                    onTriggerType(type)
                                }
                            )
                            .testTag("note_type_${type.name}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = type.color,
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = type.icon,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = type.title,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = type.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isSelected) {
                                Surface(
                                    color = type.color,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "DEFAULT",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }
}
