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
        versionCode = 5
        versionName = "0.2.0"
    }

    androidResources { noCompress += listOf("bin") }

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
    testImplementation("junit:junit:4.13.2")
}
