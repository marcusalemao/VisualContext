import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
}

/*
 * One fixed signing key for every build (07/11/2026): debug keys change with each environment and Android refuses
 * to update across keys, which forced reinstalls and wiped the device code. The key lives outside the repo:
 * ~/.vc/signing.properties (copy of ~/Rokid/_chaves/visualcontext-signing.properties on Marcus's Mac).
 */
val vcSigning = Properties().apply {
    val f = File(System.getProperty("user.home"), ".vc/signing.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "io.alema.visualcontext"
    compileSdk = 36

    signingConfigs {
        create("fixed") {
            vcSigning.getProperty("VC_KEYSTORE")?.let { storeFile = file(it.replaceFirst("~", System.getProperty("user.home"))) }
            storePassword = vcSigning.getProperty("VC_KEYSTORE_PASSWORD")
            keyAlias = vcSigning.getProperty("VC_KEY_ALIAS")
            keyPassword = vcSigning.getProperty("VC_KEY_PASSWORD")
        }
    }
    buildTypes {
        getByName("debug") { if (vcSigning.getProperty("VC_KEYSTORE") != null) signingConfig = signingConfigs.getByName("fixed") }
        getByName("release") { if (vcSigning.getProperty("VC_KEYSTORE") != null) signingConfig = signingConfigs.getByName("fixed") }
    }

    defaultConfig {
        applicationId = "io.alema.visualcontext"
        minSdk = 30
        targetSdk = 36
        versionCode = 61
        versionName = "0.18.2"
        // Rokid users' phones are 64-bit ARM; keeps the APK small (ONNX Runtime ships many ABIs).
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    // Face engines shared with the Solo app (runs on the glasses).
    sourceSets["main"].kotlin.srcDir("../../libs/face-core/src/main/kotlin")
    sourceSets["main"].assets.srcDir("../../libs/face-core/assets")
    // Gemini Live protocol, shared with the Solo app.
    sourceSets["main"].kotlin.srcDir("../../libs/live-core/src/main/kotlin")
    sourceSets["main"].kotlin.srcDir("../../libs/qr-core/src/main/kotlin")

    androidResources { noCompress += listOf("bin", "onnx") }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // Rokid Nexus plugin SDK (Apache-2.0) — https://github.com/Anezium/Rokid-Nexus
    implementation("com.github.Anezium.Rokid-Nexus:bus-client:sdk-v0.23.0")
    // Serves assets/face over https://appassets.androidplatform.net for the face engine WebView.
    implementation("androidx.webkit:webkit:1.12.1")
    // Face engine 2: Google ML Kit face detection (bundled, on-device) + ONNX Runtime for ArcFace.
    implementation("com.google.mlkit:face-detection:16.1.7")
    // Spec 5b: names under the faces in Google Photos (on the phone, Latin script).
    implementation("com.google.mlkit:text-recognition:16.0.1")
    // QR codes you look at (Pix, links, Wi-Fi, contacts): on the phone, offline.
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.20.0")
    // Emulator on the phone: camera, Gemini Live over WebSocket.
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("androidx.activity:activity:1.9.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("junit:junit:4.13.2")
}
