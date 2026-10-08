import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  // Real in-app versioning: values come from the VERSION file at the repo
  // root (bumped every release round) instead of the frozen 1.0.
  // NOTE: `java.util.Properties` cannot be fully qualified here — inside the
  // android {} block the name `java` resolves to the Java plugin extension —
  // hence the top-level import.
  val versionProps = Properties().apply {
    val f = file("${rootDir}/VERSION")
    if (f.exists()) f.inputStream().use { load(it) }
  }
  val appVersionCode = versionProps.getProperty("versionCode", "1").toInt()
  val appVersionName = versionProps.getProperty("versionName", "1.0")

  defaultConfig {
    applicationId = "com.aistudio.mnemosyne.vjrwk"
    // 26 (Android 8.0): required by androidx.health.connect:connect-client.
    // The user's device is Android 11, so nothing is lost in practice.
    minSdk = 26
    targetSdk = 36
    versionCode = appVersionCode
    versionName = appVersionName

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    // Cactus Needle 3 native engine: builds for every ABI; x86_64 and other
    // ABIs without the prebuilt engine fall back to a stub automatically.
    externalNativeBuild {
      cmake {
        arguments += listOf("-DANDROID_STL=c++_static")
      }
    }
  }

  signingConfigs {
    create("release") {
      // STABLE key committed to the repo (private, single-user app): every
      // release is signed IDENTICALLY, so Android lets the user UPDATE over
      // the previous install and the data survives. CI can still override
      // with KEYSTORE_PATH/STORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD secrets.
      val ksPath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/mnemosyne-upload.jks"
      storeFile = file(ksPath)
      storePassword = System.getenv("STORE_PASSWORD") ?: "mnemosyne2024"
      keyAlias = System.getenv("KEY_ALIAS") ?: "upload"
      keyPassword = System.getenv("KEY_PASSWORD") ?: "mnemosyne2024"
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug { signingConfig = signingConfigs.getByName("debugConfig") }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  externalNativeBuild {
    // NOTE: AGP 9 removed cmake.version — the SDK's CMake is auto-selected.
    cmake {
      path = file("src/main/cpp/CMakeLists.txt")
    }
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }

  splits {
    abi {
      isEnable = true
      reset()
      include("armeabi-v7a", "arm64-v8a", "x86_64")
      isUniversalApk = true
    }
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.mlkit.language.id)
  implementation(libs.mlkit.translate)
  implementation(libs.mlkit.entity.extraction)
  implementation(libs.mlkit.image.labeling)
  implementation(libs.mlkit.face.detection)
  implementation(libs.mlkit.text.recognition)
  implementation(libs.mlkit.barcode.scanning)
  implementation(libs.mlkit.doc.scanner)
  implementation(libs.converter.moshi)
  implementation(libs.firebase.ai)
  // Uncomment to use Firestore:
  // implementation(libs.firebase.firestore)

  // Uncomment ALL FOUR of the following dependencies together to use Firebase Auth and Google
  // Sign-In via Credential Manager:
  // implementation(libs.firebase.auth)
  // implementation(libs.androidx.credentials)
  // implementation(libs.androidx.credentials.play.services)
  // implementation(libs.googleid)
  implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // Activity Recognition Transitions API (Android 10+, real ENTER/EXIT of
  // vehicle/walking/running — feeds the habit engine and the spatial
  // travel-time estimates).
  implementation(libs.play.services.location)
  // Health Connect (Android 9+ with the provider app, built in on 14+):
  // real steps + sleep for the brief, widget and weekly report.
  implementation(libs.androidx.health.connect)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
