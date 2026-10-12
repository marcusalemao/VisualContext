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
versionCode = 3
        versionName = "0.1.2"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // Rokid Nexus plugin SDK (Apache-2.0) — https://github.com/Anezium/Rokid-Nexus
    implementation("com.github.Anezium.Rokid-Nexus:bus-client:sdk-v0.23.0")
    testImplementation("junit:junit:4.13.2")
}
