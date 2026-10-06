package com.example.ui.lens

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.semantic.BarcodeResult
import com.example.semantic.MlKitAnalyzer
import com.example.viewmodel.JournalViewModel
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Lens working modes — each maps to a bundled ML Kit model. */
enum class LensMode(val label: String) {
    TEXT("Texto"),
    TRANSLATE("Traducir"),
    CODES("Códigos"),
    OBJECTS("Objetos")
}

/** Everything the lens can extract from one shot. */
data class LensResult(
    val ocrText: String = "",
    val translatedText: String = "",
    val detectedLanguage: String = "",
    val targetLanguage: String = "",
    val barcodes: List<BarcodeResult> = emptyList(),
    val labels: List<String> = emptyList()
)

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Await CameraX's Guava ListenableFuture (the type is deliberately not
 * imported — CameraX returns com.google.common.util.concurrent.ListenableFuture,
 * which is NOT the GMS one; inferring it keeps us out of that trap).
 */
private suspend fun awaitCameraProvider(context: Context): ProcessCameraProvider {
    val future = ProcessCameraProvider.getInstance(context)
    return suspendCancellableCoroutine { cont ->
        future.addListener(
            {
                try {
                    cont.resume(future.get())
                } catch (e: Exception) {
                    cont.resumeWithException(e)
                }
            },
            Runnable::run
        )
    }
}

/** Suspend wrapper around ImageCapture.takePicture. */
private suspend fun takeShot(capture: ImageCapture, context: Context): Uri =
    suspendCancellableCoroutine { cont ->
        val file = File(context.cacheDir, "lens_${System.currentTimeMillis()}.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        capture.takePicture(
            options,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    cont.resume(Uri.fromFile(file))
                }

                override fun onError(exception: ImageCaptureException) {
                    cont.resumeWithException(exception)
                }
            }
        )
    }

/**
 * Google-Lens-style screen: point the camera (or pick a photo / scan a
 * document) and run on-device ML Kit models on it —
 *  - Text Recognition v2 (OCR, bundled, offline)
 *  - Translation (offline after one model download)
 *  - Barcode / QR scanning (bundled, offline)
 *  - Image Labeling (bundled, offline)
 *  - Document Scanner (Play Services full-page capture + cleanup)
 * Results can be copied, opened or saved as notes. Everything runs locally.
 */
@Composable
fun LensScreen(
    viewModel: JournalViewModel,
    onBack: () -> Unit,
    onNoteSaved: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var mode by remember { mutableStateOf(LensMode.TEXT) }
    var targetLang by remember { mutableStateOf("es") }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var result by remember { mutableStateOf<LensResult?>(null) }
    var analyzing by remember { mutableStateOf(false) }
    var lastShotUri by remember { mutableStateOf<Uri?>(null) }
    var statusLine by remember { mutableStateOf<String?>(null) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            statusLine = "Sin permiso de cámara: aún puedes usar galería y escáner"
        }
    }

    // ---- Camera plumbing (CameraX) ----
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    LaunchedEffect(hasCameraPermission) {
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            return@LaunchedEffect
        }
        try {
            val provider = awaitCameraProvider(context)
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
        } catch (_: Exception) {
            statusLine = "La cámara no está disponible en este dispositivo"
        }
    }

    // ---- Analysis pipeline (all on-device) ----
    fun analyze(uri: Uri) {
        lastShotUri = uri
        analyzing = true
        result = null
        statusLine = null
        scope.launch {
            val newResult = when (mode) {
                LensMode.TEXT -> {
                    val analysis = MlKitAnalyzer.analyzeImageFromUri(context, uri)
                    LensResult(ocrText = analysis.ocrText)
                }
                LensMode.TRANSLATE -> {
                    val analysis = MlKitAnalyzer.analyzeImageFromUri(context, uri)
                    val ocr = analysis.ocrText.trim()
                    if (ocr.isEmpty()) {
                        LensResult(ocrText = "")
                    } else {
                        val detected = MlKitAnalyzer.identifyLanguage(ocr)
                        val translation = MlKitAnalyzer.translateText(ocr, detected, targetLang)
                        LensResult(
                            ocrText = ocr,
                            detectedLanguage = detected,
                            targetLanguage = targetLang,
                            translatedText = translation.getOrDefault("")
                        )
                    }
                }
                LensMode.CODES -> {
                    val codes = MlKitAnalyzer.scanBarcodesFromUri(context, uri)
                    LensResult(barcodes = codes)
                }
                LensMode.OBJECTS -> {
                    val analysis = MlKitAnalyzer.analyzeImageFromUri(context, uri)
                    LensResult(labels = analysis.labels)
                }
            }
            result = newResult
            analyzing = false
            if (mode == LensMode.TEXT && newResult.ocrText.isBlank()) {
                statusLine = "No se detectó texto en la imagen"
            }
            if (mode == LensMode.CODES && newResult.barcodes.isEmpty()) {
                statusLine = "No se detectó ningún código"
            }
            if (mode == LensMode.OBJECTS && newResult.labels.isEmpty()) {
                statusLine = "No se detectaron objetos reconocibles"
            }
        }
    }

    // ---- Gallery import ----
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) analyze(uri)
    }

    // ---- Document scanner (Play Services) ----
    // NOTE: the contract is StartIntentSenderForResult (verified against the
    // actual androidx.activity 1.10.1 artifact — plain StartIntentSender does
    // NOT exist there).
    val docScanLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        try {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(activityResult.data)
            if (scanResult != null) {
                analyzing = true
                result = null
                scope.launch(Dispatchers.IO) {
                    // Prefer the cleaned page images; OCR them all.
                    val pageUris = scanResult.pages?.mapNotNull { it.imageUri } ?: emptyList()
                    val pdfUri = scanResult.pdf?.uri
                    val extractedText = StringBuilder()
                    pageUris.forEach { pageUri ->
                        val analysis = MlKitAnalyzer.analyzeImageFromUri(context, pageUri)
                        if (analysis.ocrText.isNotBlank()) {
                            if (extractedText.isNotEmpty()) extractedText.append("\n\n")
                            extractedText.append(analysis.ocrText.trim())
                        }
                    }
                    result = LensResult(
                        ocrText = extractedText.toString(),
                        barcodes = emptyList()
                    )
                    analyzing = false
                    lastShotUri = pdfUri ?: pageUris.firstOrNull()
                    statusLine = when {
                        extractedText.isBlank() && pdfUri != null -> "Documento escaneado (PDF listo para guardar)"
                        extractedText.isBlank() -> "Documento escaneado, sin texto detectado"
                        else -> null
                    }
                }
            } else {
                statusLine = "Escaneo cancelado"
            }
        } catch (_: Exception) {
            statusLine = "El escáner de documentos no está disponible (requiere Google Play Services)"
        }
    }

    fun launchDocumentScanner() {
        val activity = context.findActivity()
        if (activity == null) {
            statusLine = "No se pudo iniciar el escáner"
            return
        }
        try {
            val options = GmsDocumentScannerOptions.Builder()
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .setGalleryImportAllowed(true)
                .setPageLimit(5)
                .build()
            GmsDocumentScanning.getClient(options)
                .getStartScanIntent(activity)
                .addOnSuccessListener { intentSender ->
                    docScanLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                }
                .addOnFailureListener {
                    statusLine = "El escáner de documentos requiere Google Play Services actualizado"
                }
        } catch (_: Exception) {
            statusLine = "El escáner de documentos no está disponible"
        }
    }

    // ---------------- UI ----------------
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("lens_screen")
    ) {
        // Camera preview (only when we have permission)
        if (hasCameraPermission) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Permiso de cámara denegado",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Aún puedes analizar fotos de la galería o escanear documentos.",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
            }
            Text(
                text = "Lens",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { launchDocumentScanner() }) {
                Icon(
                    Icons.Default.DocumentScanner,
                    contentDescription = "Escanear documento",
                    tint = Color.White
                )
            }
        }

        // Mode chips + target language for translate
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 64.dp)
        ) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                LensMode.entries.forEach { lensMode ->
                    FilterChip(
                        selected = mode == lensMode,
                        onClick = {
                            mode = lensMode
                            result = null
                            statusLine = null
                        },
                        label = { Text(lensMode.label, color = Color.White, fontSize = 12.sp) },
                        leadingIcon = {
                            val icon = when (lensMode) {
                                LensMode.TEXT -> Icons.Default.TextFields
                                LensMode.TRANSLATE -> Icons.Default.Translate
                                LensMode.CODES -> Icons.Default.QrCodeScanner
                                LensMode.OBJECTS -> Icons.Default.PhotoLibrary
                            }
                            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color.Black.copy(alpha = 0.45f),
                            selectedContainerColor = Color.White.copy(alpha = 0.9f),
                            selectedLabelColor = Color.Black,
                            selectedLeadingIconColor = Color.Black
                        ),
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }

            if (mode == LensMode.TRANSLATE) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    MlKitAnalyzer.POPULAR_LANGUAGES.forEach { lang ->
                        val short = MlKitAnalyzer.languageLabel(lang.code)
                        FilterChip(
                            selected = targetLang == lang.code,
                            onClick = { targetLang = lang.code },
                            label = { Text(short, color = Color.White, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color.Black.copy(alpha = 0.45f),
                                selectedContainerColor = Color.White.copy(alpha = 0.9f),
                                selectedLabelColor = Color.Black
                            ),
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                }
            }
        }

        // Bottom controls: gallery / shutter / doc scan
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.5f),
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .clickable {
                        galleryLauncher.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.PhotoLibrary,
                        contentDescription = "Galería",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Shutter
            Surface(
                shape = CircleShape,
                color = Color.White,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .clickable(enabled = !analyzing && hasCameraPermission) {
                        scope.launch {
                            try {
                                val uri = takeShot(imageCapture, context)
                                analyze(uri)
                            } catch (_: Exception) {
                                statusLine = "No se pudo capturar la foto"
                            }
                        }
                    }
                    .testTag("lens_shutter")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (analyzing) {
                        CircularProgressIndicator(
                            color = Color.Black,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(30.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                    }
                }
            }

            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.5f),
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .clickable { launchDocumentScanner() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.DocumentScanner,
                        contentDescription = "Escanear documento",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        if (!hasCameraPermission) {
            // Shutter disabled hint
            Text(
                text = "Cámara desactivada — usa galería o escáner",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 110.dp)
            )
        }

        statusLine?.let { status ->
            Text(
                text = status,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 110.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        // Results panel
        AnimatedVisibility(
            visible = result != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            val current = result
            if (current != null) {
                LensResultPanel(
                    lensResult = current,
                    onClose = { result = null },
                    onCopy = { text ->
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(ClipData.newPlainText("lens", text))
                        Toast.makeText(context, "Copiado al portapapeles", Toast.LENGTH_SHORT).show()
                    },
                    onOpenUrl = { url ->
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        } catch (_: Exception) {
                            Toast.makeText(context, "No se pudo abrir el enlace", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onSaveNote = { title, body ->
                        viewModel.saveEntry(
                            id = 0L,
                            title = title,
                            body = body,
                            onComplete = { newId ->
                                Toast.makeText(context, "Guardado en tus notas", Toast.LENGTH_SHORT).show()
                                onNoteSaved(newId)
                            }
                        )
                    }
                )
            }
        }
    }
}

/** Short label for a translate language code. */
private fun MlKitAnalyzer.languageLabel(code: String): String = when (code) {
    "en" -> "EN"
    "es" -> "ES"
    "fr" -> "FR"
    "de" -> "DE"
    "it" -> "IT"
    "pt" -> "PT"
    "ja" -> "JA"
    "zh" -> "ZH"
    "ko" -> "KO"
    "ru" -> "RU"
    else -> code.uppercase()
}

/**
 * Bottom panel with the extraction results and real actions: copy, open
 * links, save as a note.
 */
@Composable
private fun LensResultPanel(
    lensResult: LensResult,
    onClose: () -> Unit,
    onCopy: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onSaveNote: (title: String, body: String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("lens_result_panel")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .verticalScroll(rememberScrollState())
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Resultado",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar", modifier = Modifier.size(18.dp))
                }
            }

            // ---- OCR text ----
            if (lensResult.ocrText.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Texto detectado",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = lensResult.ocrText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onCopy(lensResult.ocrText) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Copiar", fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            onSaveNote(
                                lensResult.ocrText.lineSequence().firstOrNull()?.take(40)?.ifBlank { "Texto escaneado" } ?: "Texto escaneado",
                                lensResult.ocrText
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.NoteAdd, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Guardar nota", fontSize = 12.sp)
                    }
                }
            }

            // ---- Translation ----
            if (lensResult.translatedText.isNotBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Traducción (${lensResult.detectedLanguage.uppercase()} → ${lensResult.targetLanguage.uppercase()})",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = lensResult.translatedText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onCopy(lensResult.translatedText) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Copiar", fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            onSaveNote(
                                "Traducción",
                                "Original (${lensResult.detectedLanguage}):\n${lensResult.ocrText}\n\nTraducción (${lensResult.targetLanguage}):\n${lensResult.translatedText}"
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.NoteAdd, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Guardar nota", fontSize = 12.sp)
                    }
                }
            }

            // ---- Barcodes ----
            lensResult.barcodes.forEach { barcode ->
                Spacer(modifier = Modifier.height(14.dp))
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "${barcode.formatName}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = barcode.rawValue,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        barcode.wifiSsid?.let {
                            Text(
                                text = "Wi-Fi: $it",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { onCopy(barcode.rawValue) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("Copiar", fontSize = 12.sp)
                            }
                            if (barcode.url != null) {
                                Button(
                                    onClick = { onOpenUrl(barcode.url) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text("Abrir", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // ---- Labels ----
            if (lensResult.labels.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Objetos detectados",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    lensResult.labels.forEach { label ->
                        AssistChip(
                            onClick = { onCopy(label) },
                            label = { Text(label, fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(13.dp))
                            }
                        )
                    }
                }
            }

            if (lensResult.ocrText.isBlank() &&
                lensResult.translatedText.isBlank() &&
                lensResult.barcodes.isEmpty() &&
                lensResult.labels.isEmpty()
            ) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Nada que mostrar para esta captura.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Todo el análisis ocurre en tu dispositivo (ML Kit sin conexión).",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
