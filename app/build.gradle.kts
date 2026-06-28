import java.util.Base64

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
}

val keystoreFile = file("${rootDir}/debug.keystore")
val base64File = file("${rootDir}/debug.keystore.base64")
if (!keystoreFile.exists() && base64File.exists()) {
    try {
        val base64Content = base64File.readText().trim()
        val decodedBytes = Base64.getDecoder().decode(base64Content)
        keystoreFile.writeBytes(decodedBytes)
        println("Successfully decoded debug.keystore from debug.keystore.base64")
    } catch (e: Exception) {
        println("Error decoding debug.keystore: ${e.message}")
    }
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.bartachat.kwdqp"
    minSdk = 24
    targetSdk = 34
    versionCode = 2
    versionName = "1.1"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      val releaseKeystore = file(keystorePath)
      if (releaseKeystore.exists() && System.getenv("STORE_PASSWORD") != null) {
        storeFile = releaseKeystore
        storePassword = System.getenv("STORE_PASSWORD")
        keyAlias = "upload"
        keyPassword = System.getenv("KEY_PASSWORD")
      } else {
        storeFile = file("${rootDir}/debug.keystore")
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
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

tasks.configureEach {
    if (this.name == "assembleDebug" || this.name == "assemble" || this.name == "assembleRelease") {
        finalizedBy("prepareApkDownload")
    }
}

tasks.register("prepareApkDownload") {
    val rootDirFile = rootProject.projectDir
    val apkOutputDir = layout.buildDirectory.dir("outputs/apk").get().asFile
    doLast {
        val buildOutputsDir = File(rootDirFile, ".build-outputs")
        val apkDownloadDir = File(rootDirFile, "APK_DOWNLOAD")
        
        if (!buildOutputsDir.exists()) {
            buildOutputsDir.mkdirs()
        }
        if (!apkDownloadDir.exists()) {
            apkDownloadDir.mkdirs()
        }
        
        val buildOutputsApk = File(buildOutputsDir, "app-debug.apk")
        val apkDownloadApk = File(apkDownloadDir, "app-debug.apk")
        val rootApk = File(rootDirFile, "app-debug.apk")
        
        println("=== APK Preparation Details ===")
        println("Scanning build outputs at: ${apkOutputDir.absolutePath}")
        
        var foundApk: File? = null
        if (apkOutputDir.exists()) {
            apkOutputDir.walkTopDown().forEach { file ->
                if (file.isFile && file.name.endsWith(".apk") && !file.name.contains("unaligned")) {
                    foundApk = file
                    println("Found generated APK: ${file.absolutePath} (Size: ${file.length()} bytes)")
                }
            }
        }
        
        val apkFile = foundApk ?: buildOutputsApk
        
        if (apkFile.exists()) {
            println("Preparing APK from source: ${apkFile.absolutePath} (Size: ${apkFile.length()} bytes)")
            apkFile.copyTo(buildOutputsApk, overwrite = true)
            println("Copied to ${buildOutputsApk.absolutePath}")
            apkFile.copyTo(apkDownloadApk, overwrite = true)
            println("Copied to ${apkDownloadApk.absolutePath}")
            apkFile.copyTo(rootApk, overwrite = true)
            println("Copied to ${rootApk.absolutePath}")
        } else {
            println("No fresh APK found at build directory scanning.")
            if (buildOutputsApk.exists()) {
                println("Found existing APK in .build-outputs: ${buildOutputsApk.absolutePath} (Size: ${buildOutputsApk.length()} bytes)")
                buildOutputsApk.copyTo(apkDownloadApk, overwrite = true)
                println("Copied existing APK to ${apkDownloadApk.absolutePath}")
                buildOutputsApk.copyTo(rootApk, overwrite = true)
                println("Copied existing APK to ${rootApk.absolutePath}")
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

tasks.register("printFileSizes") {
    doLast {
        val rootDirFile = rootProject.projectDir
        println("=== Large Files (>100KB) in Workspace ===")
        rootDirFile.walkTopDown().forEach { file ->
            if (file.isFile && file.length() > 100 * 1024 && !file.absolutePath.contains(".gradle") && !file.absolutePath.contains("build")) {
                println("${file.absolutePath} - ${file.length() / 1024} KB")
            }
        }
    }
}
