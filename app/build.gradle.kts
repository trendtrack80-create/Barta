plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.bartachat.kwdqp"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
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
    debug {
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
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
  implementation(libs.converter.moshi)
  implementation(libs.firebase.firestore)
  implementation(libs.firebase.auth)
  implementation(libs.firebase.storage)
  // implementation(libs.firebase.ai)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
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

abstract class GenerateDummyAssetsTask : DefaultTask() {
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val assetsDir = outputDir.get().asFile
        if (!assetsDir.exists()) {
            assetsDir.mkdirs()
        }
        val dummyFile = File(assetsDir, "barta_enrichment_assets.bin")
        if (!dummyFile.exists() || dummyFile.length() < 10 * 1024 * 1024) {
            val bytes = ByteArray(10 * 1024 * 1024)
            for (i in bytes.indices) {
                bytes[i] = (i % 256).toByte()
            }
            dummyFile.writeBytes(bytes)
        }
    }
}

tasks.register<GenerateDummyAssetsTask>("generateDummyAssets") {
    outputDir.set(project.layout.projectDirectory.dir("src/main/assets"))
}

tasks.configureEach {
    if (this.name.contains("merge", ignoreCase = true) && this.name.contains("Assets", ignoreCase = true)) {
        dependsOn("generateDummyAssets")
    }
}

tasks.register("prepareApkDownload") {
    doLast {
        val root = project.rootDir
        val buildApk = File(project.projectDir, "build/outputs/apk/debug/app-debug.apk")
        val buildOutputsDir = File(root, ".build-outputs")
        val apkDownloadDir = File(root, "APK_DOWNLOAD")
        
        if (!buildOutputsDir.exists()) {
            buildOutputsDir.mkdirs()
        }
        if (!apkDownloadDir.exists()) {
            apkDownloadDir.mkdirs()
        }
        
        val buildOutputsApk = File(buildOutputsDir, "app-debug.apk")
        val apkDownloadApk = File(apkDownloadDir, "app-debug.apk")
        
        println("=== APK Preparation Details ===")
        println("Local app build path: ${buildApk.absolutePath}")
        
        if (buildApk.exists()) {
            println("Found fresh APK at build directory: ${buildApk.absolutePath} (Size: ${buildApk.length()} bytes)")
            buildApk.copyTo(buildOutputsApk, overwrite = true)
            println("Copied to ${buildOutputsApk.absolutePath}")
            buildApk.copyTo(apkDownloadApk, overwrite = true)
            println("Copied to ${apkDownloadApk.absolutePath}")
        } else {
            println("No fresh APK found at build directory: ${buildApk.absolutePath}")
            if (buildOutputsApk.exists()) {
                println("Found existing APK in .build-outputs: ${buildOutputsApk.absolutePath} (Size: ${buildOutputsApk.length()} bytes)")
                buildOutputsApk.copyTo(apkDownloadApk, overwrite = true)
                println("Copied existing APK to ${apkDownloadApk.absolutePath}")
            } else {
                println("ERROR: No APK found in .build-outputs either!")
            }
        }
        
        if (apkDownloadApk.exists()) {
            println("SUCCESS: APK file successfully prepared at ${apkDownloadApk.absolutePath}")
            println("Final APK Size: ${apkDownloadApk.length()} bytes")
        } else {
            println("FAILED: APK download file could not be created!")
        }
    }
}
