plugins {
    id("com.android.application")
}

android {
    namespace = "io.alema.qrreader"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.alema.qrreader"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // The QR parser is shared with VisualContext: plain Kotlin, no Android, tested on its own.
    sourceSets["main"].kotlin.srcDir("../../libs/qr-core/src/main/kotlin")
}

dependencies {
    // Rokid Nexus plugin SDK (Apache-2.0) — https://github.com/Anezium/Rokid-Nexus
    implementation("com.github.Anezium.Rokid-Nexus:bus-client:sdk-v0.23.0")
    // ZXing core (Apache-2.0): pure-Java QR decoding, works offline, no Google Play services
    implementation("com.google.zxing:core:3.5.3")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.1")
}

tasks.withType<Test>().configureEach { useJUnitPlatform() }
