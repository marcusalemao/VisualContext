plugins {
    id("com.android.application")
}

android {
    namespace = "io.alema.visualcontext"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.alema.visualcontext"
        minSdk = 30
        targetSdk = 36
        versionCode = 6
        versionName = "0.3.0"
        // Rokid users' phones are 64-bit ARM; keeps the APK small (ONNX Runtime ships many ABIs).
        ndk { abiFilters += listOf("arm64-v8a") }
    }

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
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.20.0")
    testImplementation("junit:junit:4.13.2")
}
