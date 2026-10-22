plugins {
    id("com.android.application")
}

// VisualContext Solo: runs ON the glasses (no phone, no Nexus). Wi-Fi from any access point or a watch hotspot.
android {
    namespace = "io.alema.vcsolo"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.alema.vcsolo"
        minSdk = 30          // Rokid Glasses: YodaOS = Android 12 (API 32)
        targetSdk = 32
        versionCode = 4
        versionName = "0.1.3"
    }

    // QR hints shared with the Leitor QR plugin: plain Kotlin, no Android.
    sourceSets["main"].kotlin.srcDir("../../libs/qr-core/src/main/kotlin")
    // Gemini Live protocol + WebSocket, shared with the VisualContext Live Nexus plugin.
    sourceSets["main"].kotlin.srcDir("../../libs/live-core/src/main/kotlin")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")          // Apache-2.0 — WebSocket to the Gemini Live API
    implementation("androidx.camera:camera-core:1.3.4")           // Apache-2.0
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")             // live view while aiming at the setup QR
    implementation("androidx.activity:activity:1.9.3")
    implementation("com.google.zxing:core:3.5.3")                  // Apache-2.0 — reads the setup QR offline
    testImplementation("org.json:json:20240303")                  // org.json on the JVM for protocol tests
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.1")
}

tasks.withType<Test>().configureEach { useJUnitPlatform() }
