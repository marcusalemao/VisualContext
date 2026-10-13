plugins {
    id("com.android.application")
}

android {
    namespace = "io.alema.zap"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.alema.zap"
        // Nexus phone hub supports Android 11+; do not ask for more.
        minSdk = 30
        targetSdk = 36
        versionCode = 5
        versionName = "0.2.1"
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":core"))
    implementation("com.github.Anezium.Rokid-Nexus:bus-client:sdk-v0.23.0")
    testImplementation("junit:junit:4.13.2")
}
