plugins {
    id("com.android.application")
}

android {
    namespace = "io.alema.wifiadb"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.alema.wifiadb"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // Rokid Nexus plugin SDK (Apache-2.0) — https://github.com/Anezium/Rokid-Nexus
    implementation("com.github.Anezium.Rokid-Nexus:bus-client:sdk-v0.23.0")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.1")
}

tasks.withType<Test>().configureEach { useJUnitPlatform() }
