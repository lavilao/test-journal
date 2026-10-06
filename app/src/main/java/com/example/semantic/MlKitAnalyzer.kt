package com.example.semantic

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.example.data.model.EntityType
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.entityextraction.DateTimeEntity
import com.google.mlkit.nl.entityextraction.Entity
import com.google.mlkit.nl.entityextraction.EntityAnnotation
import com.google.mlkit.nl.entityextraction.EntityExtraction
import com.google.mlkit.nl.entityextraction.EntityExtractionParams
import com.google.mlkit.nl.entityextraction.EntityExtractor
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions
import com.google.mlkit.nl.entityextraction.MoneyEntity
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ImageAnalysisResult(
    val labels: List<String> = emptyList(),
    val faceCount: Int = 0,
    val ocrText: String = "",
    /** Values of barcodes/QR codes found in the image. */
    val barcodes: List<String> = emptyList()
)

data class BarcodeResult(
    val rawValue: String,
    val formatName: String,
    val url: String? = null,
    val wifiSsid: String? = null
)

data class SupportedLanguage(
    val code: String,
    val displayName: String
)

data class MlKitRawEntity(
    val rawText: String,
    val startOffset: Int,
    val endOffset: Int,
    val type: EntityType,
    val normalizedValue: String = "",
    val confidence: Float = 0.90f
)

object MlKitAnalyzer {

    val POPULAR_LANGUAGES = listOf(
        SupportedLanguage(TranslateLanguage.ENGLISH, "English"),
        SupportedLanguage(TranslateLanguage.SPANISH, "Spanish (Español)"),
        SupportedLanguage(TranslateLanguage.FRENCH, "French (Français)"),
        SupportedLanguage(TranslateLanguage.GERMAN, "German (Deutsch)"),
        SupportedLanguage(TranslateLanguage.ITALIAN, "Italian (Italiano)"),
        SupportedLanguage(TranslateLanguage.PORTUGUESE, "Portuguese (Português)"),
        SupportedLanguage(TranslateLanguage.JAPANESE, "Japanese (日本語)"),
        SupportedLanguage(TranslateLanguage.CHINESE, "Chinese (中文)"),
        SupportedLanguage(TranslateLanguage.KOREAN, "Korean (한국어)"),
        SupportedLanguage(TranslateLanguage.RUSSIAN, "Russian (Русский)")
    )

    /**
     * Identify the language of the text using ML Kit on-device Language Identification.
     */
    suspend fun identifyLanguage(text: String): String = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext "en"
        try {
            val languageIdentifier = LanguageIdentification.getClient()
            val task = languageIdentifier.identifyLanguage(text)
            val langCode = Tasks.await(task)
            languageIdentifier.close()
            if (langCode == "und" || langCode.isNullOrBlank()) "en" else langCode
        } catch (_: Exception) {
            "en"
        }
    }

    /**
     * Checks if ML Kit Entity Extraction model is downloaded on device.
     */
    suspend fun isEntityModelDownloaded(modelIdentifier: String = EntityExtractorOptions.ENGLISH): Boolean = withContext(Dispatchers.IO) {
        try {
            val extractor = EntityExtraction.getClient(
                EntityExtractorOptions.Builder(modelIdentifier).build()
            )
            val downloaded = Tasks.await(extractor.isModelDownloaded) ?: false
            extractor.close()
            downloaded
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Downloads the ML Kit Entity Extraction model if needed.
     */
    suspend fun downloadEntityModel(modelIdentifier: String = EntityExtractorOptions.ENGLISH): Boolean = withContext(Dispatchers.IO) {
        try {
            val extractor = EntityExtraction.getClient(
                EntityExtractorOptions.Builder(modelIdentifier).build()
            )
            val conditions = DownloadConditions.Builder().build()
            val task = extractor.downloadModelIfNeeded(conditions)
            Tasks.await(task)
            extractor.close()
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Extracts entities using Google ML Kit on-device Entity Extraction.
     * Extracts Dates, Addresses, URLs, Phone numbers, Emails, Money, etc. with exact offsets.
     */
    suspend fun extractEntitiesWithMlKit(
        text: String,
        modelIdentifier: String = EntityExtractorOptions.ENGLISH
    ): List<MlKitRawEntity> = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext emptyList()
        val results = mutableListOf<MlKitRawEntity>()
        try {
            val extractor = EntityExtraction.getClient(
                EntityExtractorOptions.Builder(modelIdentifier).build()
            )
            // Attempt to download if not present or run if downloaded
            val isDownloaded = isEntityModelDownloaded(modelIdentifier)
            if (!isDownloaded) {
                try {
                    val conditions = DownloadConditions.Builder().build()
                    Tasks.await(extractor.downloadModelIfNeeded(conditions))
                } catch (_: Exception) {
                    // Fall back to rule-based engine if model download fails or offline
                    extractor.close()
                    return@withContext emptyList()
                }
            }

            val params = EntityExtractionParams.Builder(text).build()
            val annotations = Tasks.await(extractor.annotate(params))

            for (annotation in annotations) {
                val rawText = annotation.annotatedText
                val start = annotation.start
                val end = annotation.end

                for (entity in annotation.entities) {
                    val (type, normalized) = when (entity.type) {
                        Entity.TYPE_DATE_TIME -> {
                            val dt = entity.asDateTimeEntity()
                            EntityType.DATE to (dt?.timestampMillis?.toString() ?: rawText)
                        }
                        Entity.TYPE_ADDRESS -> EntityType.ADDRESS to rawText
                        Entity.TYPE_EMAIL -> EntityType.EMAIL to rawText.lowercase()
                        Entity.TYPE_PHONE -> EntityType.PHONE to rawText
                        Entity.TYPE_URL -> EntityType.URL to rawText
                        Entity.TYPE_MONEY -> {
                            val money = entity.asMoneyEntity()
                            EntityType.MONEY to ("${money?.unnormalizedCurrency ?: ""} ${money?.integerPart ?: ""}.${money?.fractionalPart ?: ""}".trim())
                        }
                        else -> EntityType.OTHER to rawText
                    }

                    if (type != EntityType.OTHER) {
                        results.add(
                            MlKitRawEntity(
                                rawText = rawText,
                                startOffset = start,
                                endOffset = end,
                                type = type,
                                normalizedValue = normalized,
                                confidence = 0.95f
                            )
                        )
                    }
                }
            }
            extractor.close()
        } catch (_: Exception) {
            // Graceful fallback
        }
        results
    }

    /**
     * Checks if a translation model is already downloaded locally on the device.
     */
    suspend fun isModelDownloaded(languageCode: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val modelManager = RemoteModelManager.getInstance()
            val model = TranslateRemoteModel.Builder(languageCode).build()
            val task = modelManager.isModelDownloaded(model)
            Tasks.await(task) ?: false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Downloads an offline translation model.
     */
    suspend fun downloadTranslationModel(
        languageCode: String,
        requireWifi: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val modelManager = RemoteModelManager.getInstance()
            val model = TranslateRemoteModel.Builder(languageCode).build()
            val conditionsBuilder = DownloadConditions.Builder()
            if (requireWifi) {
                conditionsBuilder.requireWifi()
            }
            val conditions = conditionsBuilder.build()
            val task = modelManager.download(model, conditions)
            Tasks.await(task)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Deletes a downloaded translation model to free storage.
     */
    suspend fun deleteTranslationModel(languageCode: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val modelManager = RemoteModelManager.getInstance()
            val model = TranslateRemoteModel.Builder(languageCode).build()
            val task = modelManager.deleteDownloadedModel(model)
            Tasks.await(task)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Translates text offline using ML Kit Translation.
     */
    suspend fun translateText(
        text: String,
        sourceLanguage: String,
        targetLanguage: String
    ): Result<String> = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext Result.success(text)
        try {
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(sourceLanguage)
                .setTargetLanguage(targetLanguage)
                .build()
            val translator = Translation.getClient(options)

            val conditions = DownloadConditions.Builder().build()
            Tasks.await(translator.downloadModelIfNeeded(conditions))

            val translateTask = translator.translate(text)
            val translated = Tasks.await(translateTask)
            translator.close()
            Result.success(translated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Scans barcodes & QR codes (ML Kit Barcode Scanning, bundled model).
     */
    suspend fun scanBarcodes(bitmap: Bitmap): List<BarcodeResult> = withContext(Dispatchers.IO) {
        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build()
            val scanner = BarcodeScanning.getClient(options)
            val barcodes = Tasks.await(scanner.process(inputImage))
            scanner.close()
            barcodes.mapNotNull { code ->
                val raw = code.rawValue ?: code.rawBytes?.toString(Charsets.UTF_8) ?: return@mapNotNull null
                BarcodeResult(
                    rawValue = raw,
                    formatName = code.formatString(),
                    url = code.url?.url,
                    wifiSsid = code.wifi?.ssid
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun Barcode.formatString(): String = when (format) {
        Barcode.FORMAT_QR_CODE -> "QR"
        Barcode.FORMAT_EAN_13 -> "EAN-13"
        Barcode.FORMAT_EAN_8 -> "EAN-8"
        Barcode.FORMAT_UPC_A -> "UPC-A"
        Barcode.FORMAT_UPC_E -> "UPC-E"
        Barcode.FORMAT_CODE_128 -> "Code 128"
        Barcode.FORMAT_CODE_39 -> "Code 39"
        Barcode.FORMAT_CODE_93 -> "Code 93"
        Barcode.FORMAT_CODABAR -> "Codabar"
        Barcode.FORMAT_ITF -> "ITF"
        Barcode.FORMAT_PDF417 -> "PDF417"
        Barcode.FORMAT_AZTEC -> "Aztec"
        Barcode.FORMAT_DATA_MATRIX -> "Data Matrix"
        else -> "Código"
    }

    /**
     * Scans barcodes from a Uri (EXIF rotation handled by InputImage).
     */
    suspend fun scanBarcodesFromUri(context: Context, uri: Uri): List<BarcodeResult> = withContext(Dispatchers.IO) {
        try {
            val inputImage = InputImage.fromFilePath(context, uri)
            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build()
            val scanner = BarcodeScanning.getClient(options)
            val barcodes = Tasks.await(scanner.process(inputImage))
            scanner.close()
            barcodes.mapNotNull { code ->
                val raw = code.rawValue ?: return@mapNotNull null
                BarcodeResult(
                    rawValue = raw,
                    formatName = code.formatString(),
                    url = code.url?.url,
                    wifiSsid = code.wifi?.ssid
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Analyzes an image Bitmap with ML Kit:
     * 1. Image Labeling (detect objects & scenes)
     * 2. Face Detection (count faces)
     * 3. Text Recognition / OCR
     */
    suspend fun analyzeImage(bitmap: Bitmap): ImageAnalysisResult = withContext(Dispatchers.IO) {
        var labels = emptyList<String>()
        var faceCount = 0
        var ocrText = ""

        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)

            runCatching {
                val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
                val labelTask = labeler.process(inputImage)
                val results = Tasks.await(labelTask)
                labels = results.filter { it.confidence >= 0.65f }.map { it.text }
                labeler.close()
            }

            runCatching {
                val faceOptions = FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                    .build()
                val detector = FaceDetection.getClient(faceOptions)
                val faceTask = detector.process(inputImage)
                val faces = Tasks.await(faceTask)
                faceCount = faces.size
                detector.close()
            }

            runCatching {
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                val ocrTask = recognizer.process(inputImage)
                val visionText = Tasks.await(ocrTask)
                ocrText = visionText.text
                recognizer.close()
            }
        } catch (_: Exception) {
            // Graceful fallback
        }

        ImageAnalysisResult(labels = labels, faceCount = faceCount, ocrText = ocrText)
    }

    /**
     * Analyzes image from Android URI.
     */
    suspend fun analyzeImageFromUri(context: Context, uri: Uri): ImageAnalysisResult = withContext(Dispatchers.IO) {
        try {
            val inputImage = InputImage.fromFilePath(context, uri)
            var labels = emptyList<String>()
            var faceCount = 0
            var ocrText = ""

            runCatching {
                val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
                val labelTask = Tasks.await(labeler.process(inputImage))
                labels = labelTask.filter { it.confidence >= 0.65f }.map { it.text }
                labeler.close()
            }

            runCatching {
                val detector = FaceDetection.getClient(
                    FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                        .build()
                )
                val faces = Tasks.await(detector.process(inputImage))
                faceCount = faces.size
                detector.close()
            }

            runCatching {
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                val textResult = Tasks.await(recognizer.process(inputImage))
                ocrText = textResult.text
                recognizer.close()
            }

            ImageAnalysisResult(labels = labels, faceCount = faceCount, ocrText = ocrText)
        } catch (_: Exception) {
            ImageAnalysisResult()
        }
    }
}
