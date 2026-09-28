package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.example.ui.components.VoiceTranscriptionModal
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.EntryWithRelations
import com.example.ui.components.EditorialCard
import com.example.ui.components.EntityChip
import com.example.ui.components.TagChip
import com.example.ui.theme.AmberNode
import com.example.ui.theme.ForestPrimary
import com.example.ui.theme.TerracottaAccent
import com.example.viewmodel.JournalViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TimelineScreen(
    viewModel: JournalViewModel,
    onNavigateToNewEntry: () -> Unit,
    onNavigateToDetail: (Long) -> Unit,
    onNavigateToEntity: (Long) -> Unit
) {
    val entries by viewModel.filteredEntries.collectAsState()
    val allEntities by viewModel.entities.collectAsState()
    val events by viewModel.events.collectAsState()
    val forgottenThreads by viewModel.forgottenThreads.collectAsState()
    val onThisDay by viewModel.onThisDayMemories.collectAsState()
    val filterTag by viewModel.timelineFilterTag.collectAsState()
    val filterEntity by viewModel.timelineFilterEntity.collectAsState()

    var showEventsView by remember { mutableStateOf(false) }
    var showQuickDictateModal by remember { mutableStateOf(false) }

    val dateFormat = SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault())

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("timeline_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(
                        text = "Mnemosyne",
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Offline Semantic Journal & Memory Graph",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Stats Summary Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${entries.size}",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = ForestPrimary
                            )
                            Text(
                                text = "Memories",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column {
                            Text(
                                text = "${allEntities.size}",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = TerracottaAccent
                            )
                            Text(
                                text = "Entities",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column {
                            Text(
                                text = "${events.size}",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = AmberNode
                            )
                            Text(
                                text = "Episodes",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // View Mode Switcher
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = !showEventsView,
                            onClick = { showEventsView = false },
                            label = { Text("Daily Timeline") },
                            modifier = Modifier.testTag("filter_daily_timeline")
                        )
                        FilterChip(
                            selected = showEventsView,
                            onClick = { showEventsView = true },
                            label = { Text("Grouped Episodes (${events.size})") },
                            modifier = Modifier.testTag("filter_grouped_episodes")
                        )
                    }
                }
            }

            // On This Day Resurfacing Card
            if (onThisDay.isNotEmpty()) {
                item {
                    val memory = onThisDay.first()
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToDetail(memory.id) }
                            .testTag("on_this_day_card")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "On This Day in the Past",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = memory.title.ifBlank { "Untitled memory" },
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Forgotten Threads Card
            if (forgottenThreads.isNotEmpty()) {
                val thread = forgottenThreads.first()
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToEntity(thread.id) }
                            .testTag("forgotten_thread_card")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = AmberNode.copy(alpha = 0.2f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = null,
                                        tint = AmberNode,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Forgotten Thread: ${thread.displayName}",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${thread.mentionCount} mentions in your journal. Hasn't been written about recently.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Active Filters Banner if set
            if (filterTag != null || filterEntity != null) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Filtered by: ${listOfNotNull(filterTag?.let { "#$it" }, filterEntity).joinToString(", ")}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        IconButton(
                            onClick = { viewModel.clearFilters() },
                            modifier = Modifier.size(24.dp).testTag("clear_filter_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear Filter",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Grouped Episodes View OR Daily Timeline
            if (showEventsView) {
                if (events.isEmpty()) {
                    item {
                        Text(
                            text = "No grouped episodes detected yet. Continue journaling to form timeline clusters.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(events, key = { it.id }) { event ->
                        EditorialCard(modifier = Modifier.testTag("event_card_${event.id}")) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = event.title,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Surface(
                                        color = AmberNode.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "${event.entryCount} entries",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = AmberNode,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = event.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (!event.location.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = ForestPrimary, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(event.location, style = MaterialTheme.typography.labelSmall, color = ForestPrimary)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Journal Entries Stream
                if (entries.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No journal entries found",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Tap the + button to record your first private memory",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    }
                } else {
                    items(entries, key = { it.entry.id }) { item ->
                        JournalEntryTimelineCard(
                            item = item,
                            formattedDate = dateFormat.format(Date(item.entry.journalDate)),
                            onClick = { onNavigateToDetail(item.entry.id) },
                            onEntityClick = onNavigateToEntity,
                            onTagClick = { tag -> viewModel.setTagFilter(tag) }
                        )
                    }
                }
            }
        }

        // Floating Action Buttons (Quick Dictate & Add Entry)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SmallFloatingActionButton(
                onClick = { showQuickDictateModal = true },
                containerColor = TerracottaAccent,
                contentColor = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.testTag("quick_dictate_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Quick Voice Dictation",
                    modifier = Modifier.size(20.dp)
                )
            }

            FloatingActionButton(
                onClick = onNavigateToNewEntry,
                containerColor = ForestPrimary,
                contentColor = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.testTag("create_entry_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New Journal Entry",
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }

    if (showQuickDictateModal) {
        VoiceTranscriptionModal(
            title = "Quick Spoken Reflection",
            initialTranscript = "",
            audioFilePath = null,
            durationMs = 0L,
            viewModel = viewModel,
            onDismiss = { showQuickDictateModal = false },
            onSaveTranscript = { transcript ->
                if (transcript.isNotBlank()) {
                    viewModel.createQuickVoiceMemory(
                        title = "Spoken Reflection",
                        transcript = transcript,
                        onCreated = { newId ->
                            onNavigateToDetail(newId)
                        }
                    )
                }
            }
        )
    }
}

@Composable
private fun JournalEntryTimelineCard(
    item: EntryWithRelations,
    formattedDate: String,
    onClick: () -> Unit,
    onEntityClick: (Long) -> Unit,
    onTagClick: (String) -> Unit
) {
    val entry = item.entry

    EditorialCard(
        onClick = onClick,
        modifier = Modifier.testTag("entry_card_${entry.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Date & Metadata Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Serif,
                        letterSpacing = 0.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!entry.mood.isNullOrBlank()) {
                        Surface(
                            color = TerracottaAccent.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Text(
                                text = entry.mood,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = TerracottaAccent,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (!entry.location.isNullOrBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = entry.location,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Title
            Text(
                text = entry.title.ifBlank { "Untitled Note" },
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Body Snippet
            Text(
                text = entry.body,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            // Optional Image Attachment Preview
            if (!entry.imageUri.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                AsyncImage(
                    model = entry.imageUri,
                    contentDescription = "Attached photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            }

            // Entities Row (Discovered people, places, topics)
            if (item.entities.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item.entities.take(4).forEach { entity ->
                        EntityChip(
                            name = entity.displayName,
                            type = entity.type,
                            onClick = { onEntityClick(entity.id) }
                        )
                    }
                }
            }

            // Tags Row
            if (item.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item.tags.take(5).forEach { tag ->
                        TagChip(
                            tag = tag.name,
                            onClick = { onTagClick(tag.name) }
                        )
                    }
                }
            }

            // Word count and Knowledge link indicator
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${entry.wordCount} words • ${entry.language?.uppercase() ?: "EN"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Hub,
                        contentDescription = null,
                        tint = AmberNode,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Semantic graph node",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = AmberNode
                    )
                }
            }
        }
    }
}
