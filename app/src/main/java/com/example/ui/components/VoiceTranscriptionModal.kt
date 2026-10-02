package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.media.PlaybackState
import com.example.ui.theme.AmberNode
import com.example.ui.theme.ForestPrimary
import com.example.ui.theme.TerracottaAccent
import com.example.viewmodel.JournalViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceTranscriptionModal(
    title: String = "Voice Transcription & Dictation",
    initialTranscript: String = "",
    audioFilePath: String? = null,
    durationMs: Long = 0,
    viewModel: JournalViewModel,
    onDismiss: () -> Unit,
    onSaveTranscript: (String) -> Unit
) {
    val context = LocalContext.current
    var transcriptText by remember { mutableStateOf(initialTranscript) }
    val isDictating by viewModel.isDictating.collectAsState()
    val playbackState by viewModel.voiceManager.playbackState.collectAsState()
    val currentPlayingPath by viewModel.voiceManager.currentPlayingPath.collectAsState()

    val isAudioPlaying = playbackState == PlaybackState.PLAYING && currentPlayingPath == audioFilePath

    val sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startDictation(
                onResult = { recognized ->
                    transcriptText = if (transcriptText.isBlank()) recognized else "$transcriptText $recognized"
                },
                onError = {
                    Toast.makeText(context, "Microphone offline speech recognition not ready. You can type or insert sample text.", Toast.LENGTH_LONG).show()
                }
            )
        } else {
            Toast.makeText(context, "Microphone permission needed for dictation", Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopDictation()
            if (audioFilePath != null) {
                viewModel.stopAudio()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("voice_transcription_modal")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.RecordVoiceOver,
                        contentDescription = null,
                        tint = ForestPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Speech-to-text dictation & offline audio transcription",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = ForestPrimary.copy(alpha = 0.12f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = ForestPrimary,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Bundled English Model: Ready (100% Offline • No Gboard Needed)",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                                    color = ForestPrimary
                                )
                            }
                        }
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_transcription_modal_btn")) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Audio Player Bar (if audio file exists)
            if (!audioFilePath.isNullOrBlank()) {
                var isTranscribingAudio by remember { mutableStateOf(false) }
                val scope = androidx.compose.runtime.rememberCoroutineScope()

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("modal_audio_player_card")
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        if (isAudioPlaying) {
                                            viewModel.stopAudio()
                                        } else {
                                            viewModel.playAudio(audioFilePath)
                                        }
                                    },
                                    modifier = Modifier.testTag("modal_play_pause_audio_btn")
                                ) {
                                    Icon(
                                        imageVector = if (isAudioPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (isAudioPlaying) "Pause" else "Play",
                                        tint = ForestPrimary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = if (isAudioPlaying) "Playing Recorded Audio..." else "Recorded Audio File",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    val sec = durationMs / 1000
                                    Text(
                                        text = "${sec / 60}m ${sec % 60}s duration",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (isAudioPlaying) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(ForestPrimary, CircleShape)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Button(
                            onClick = {
                                isTranscribingAudio = true
                                scope.launch {
                                    viewModel.voiceManager.transcribeAudioOffline(audioFilePath) { transcript, _ ->
                                        isTranscribingAudio = false
                                        if (transcript.isNotBlank()) {
                                            transcriptText = if (transcriptText.isBlank()) transcript else "$transcriptText\n\n$transcript"
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ForestPrimary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("transcribe_audio_file_btn")
                        ) {
                            if (isTranscribingAudio) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Transcribing Audio with Bundled Engine...", fontSize = 12.sp)
                            } else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Transcribe Audio (Bundled English Engine)", fontSize = 12.sp)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Dictation Control Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isDictating) TerracottaAccent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isDictating) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = TerracottaAccent, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Listening... Speak clearly",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = TerracottaAccent
                            )
                        } else {
                            Icon(Icons.Default.Mic, contentDescription = null, tint = ForestPrimary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Live Dictation",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = ForestPrimary
                            )
                        }
                    }

                    if (isDictating) {
                        Button(
                            onClick = { viewModel.stopDictation() },
                            colors = ButtonDefaults.buttonColors(containerColor = TerracottaAccent),
                            modifier = Modifier.testTag("modal_stop_dictation_btn")
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Stop", fontSize = 12.sp)
                        }
                    } else {
                        Button(
                            onClick = {
                                val hasPerm = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasPerm) {
                                    viewModel.startDictation(
                                        onResult = { recognized ->
                                            transcriptText = if (transcriptText.isBlank()) recognized else "$transcriptText $recognized"
                                        },
                                        onError = {
                                            Toast.makeText(context, "Microphone recognition offline unavailable. You can type or use sample below.", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                } else {
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ForestPrimary),
                            modifier = Modifier.testTag("modal_start_dictation_btn")
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Start Speaking", fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick helpers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        val sample = "Reflected with Sarah at Starbucks today about building offline knowledge graph architectures and cognitive mapping."
                        transcriptText = if (transcriptText.isBlank()) sample else "$transcriptText $sample"
                    },
                    modifier = Modifier.testTag("modal_sample_transcript_btn")
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AmberNode, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Insert Sample Note", fontSize = 11.sp)
                }

                if (transcriptText.isNotBlank()) {
                    TextButton(onClick = { transcriptText = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear", fontSize = 11.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Editable Transcript Body
            OutlinedTextField(
                value = transcriptText,
                onValueChange = { transcriptText = it },
                label = { Text("Transcript / Dictated Text") },
                placeholder = { Text("Spoken words will appear here in real-time, or you can type directly...") },
                minLines = 5,
                maxLines = 10,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ForestPrimary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("modal_transcript_text_input")
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Footer Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = {
                        onSaveTranscript(transcriptText)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ForestPrimary),
                    modifier = Modifier.testTag("save_transcript_confirm_btn")
                ) {
                    Text("Save & Apply Transcript")
                }
            }
        }
    }
}
