package com.example.semantic

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
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
    val ocrText: String = ""
)

data class SupportedLanguage(
    val code: String,
    val displayName: String
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
     * Returns "en" if undetermined or failed.
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

            // Ensure model conditions
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

            // 1. Image Labels
            runCatching {
                val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
                val labelTask = labeler.process(inputImage)
                val results = Tasks.await(labelTask)
                labels = results.filter { it.confidence >= 0.65f }.map { it.text }
                labeler.close()
            }

            // 2. Face Detection
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

            // 3. OCR Text Recognition
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
                val detector = FaceDetection.getClient(FaceDetectorOptions.Builder().setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST).build())
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
