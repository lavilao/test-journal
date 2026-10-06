package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
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
import com.example.ui.theme.ForestPrimary
import com.example.ui.theme.TerracottaAccent
import com.example.viewmodel.JournalViewModel
import java.util.Locale

/**
 * Dictation sheet — honest by design: it uses the Android system speech
 * recognizer for live dictation. There is no fake "model download", no
 * bundled engine, and no sample text button. If the device has no speech
 * service, it says so.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceTranscriptionModal(
    title: String = "Dictado de voz",
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
    val dictationAvailable = viewModel.isDictationAvailable

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
                    Toast.makeText(
                        context,
                        "El reconocimiento de voz no está disponible ahora. Puedes escribir directamente.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        } else {
            Toast.makeText(context, "Se necesita permiso del micrófono para dictar", Toast.LENGTH_SHORT).show()
        }
    }

    // Stop any dictation session when leaving the sheet.
    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopDictation()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = null,
                    tint = if (isDictating) TerracottaAccent else ForestPrimary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f)
                )
                if (isDictating) {
                    TextButton(onClick = { viewModel.stopDictation() }) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(14.dp))
                        Text("Detener")
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (dictationAvailable) {
                    "Habla y el texto aparecerá aquí. También puedes editarlo a mano."
                } else {
                    "Este dispositivo no tiene servicio de reconocimiento de voz. Escribe directamente."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Playback of the attached recording, if any
            if (!audioFilePath.isNullOrBlank()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { if (isAudioPlaying) viewModel.stopAudio() else viewModel.playAudio(audioFilePath) }) {
                            Icon(
                                if (isAudioPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Reproducir",
                                tint = ForestPrimary
                            )
                        }
                        val sec = durationMs / 1000
                        Text(
                            text = "Grabación · ${sec / 60}:${String.format(Locale.US, "%02d", sec % 60)}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Dictation control
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isDictating) TerracottaAccent.copy(alpha = 0.12f)
                    else ForestPrimary.copy(alpha = 0.08f)
                ),
                shape = RoundedCornerShape(12.dp)
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
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = TerracottaAccent,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Escuchando…",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = TerracottaAccent
                            )
                        } else {
                            Icon(Icons.Default.Mic, contentDescription = null, tint = ForestPrimary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Dictado en vivo",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = ForestPrimary
                            )
                        }
                    }
                    if (!isDictating) {
                        Button(
                            onClick = {
                                val hasPerm = ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasPerm) {
                                    viewModel.startDictation(
                                        onResult = { recognized ->
                                            transcriptText = if (transcriptText.isBlank()) recognized else "$transcriptText $recognized"
                                        },
                                        onError = {
                                            Toast.makeText(context, "Reconocimiento de voz no disponible. Puedes escribir.", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                } else {
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            enabled = dictationAvailable,
                            colors = ButtonDefaults.buttonColors(containerColor = ForestPrimary),
                            modifier = Modifier.testTag("modal_start_dictation_btn")
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Hablar", fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Editable transcript
            OutlinedTextField(
                value = transcriptText,
                onValueChange = { transcriptText = it },
                label = { Text("Texto") },
                placeholder = { Text("Lo que digas o escribas aparecerá aquí…") },
                minLines = 5,
                maxLines = 10,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("modal_transcript_text_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (transcriptText.isNotBlank()) {
                    TextButton(onClick = { transcriptText = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(14.dp))
                        Text("Limpiar")
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        onSaveTranscript(transcriptText)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ForestPrimary)
                ) {
                    Text("Guardar")
                }
            }
        }
    }
}
