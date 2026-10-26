plugins {
    id("com.android.application")
}

// VisualContext Live (test): Gemini Live inside a Nexus plugin. The phone talks to Gemini;
// the glasses give the mic (Nexus audio lease) and photos (Nexus snapshots). Question to answer:
// does Gemini's own voice come out of the glasses (phone audio over Bluetooth) or only from the phone?
android {
    namespace = "io.alema.vclive"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.alema.vclive"
        minSdk = 30
        targetSdk = 36
        versionCode = 2
        versionName = "0.1.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    sourceSets["main"].kotlin.srcDir("../../libs/live-core/src/main/kotlin")
}

dependencies {
    // Rokid Nexus plugin SDK (Apache-2.0) — https://github.com/Anezium/Rokid-Nexus
    implementation("com.github.Anezium.Rokid-Nexus:bus-client:sdk-v0.23.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")          // Apache-2.0
}
