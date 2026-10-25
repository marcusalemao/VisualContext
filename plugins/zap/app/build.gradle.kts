import java.util.Properties

plugins {
    id("com.android.application")
}

/**
 * Release signing lives outside the repo: ~/.zap/signing.properties (or the
 * ZAP_* environment variables). Never commit the keystore or its passwords.
 */
val signing = Properties().apply {
    val file = File(System.getProperty("user.home"), ".zap/signing.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun secret(key: String): String? = System.getenv(key) ?: signing.getProperty(key)

android {
    namespace = "io.alema.zap"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.alema.zap"
        // Nexus phone hub supports Android 11+; do not ask for more.
        minSdk = 30
        targetSdk = 36
        versionCode = 36
        versionName = "0.13.2"
    }

    signingConfigs {
        create("release") {
            secret("ZAP_KEYSTORE")?.let { storeFile = File(it) }
            storePassword = secret("ZAP_KEYSTORE_PASSWORD")
            keyAlias = secret("ZAP_KEY_ALIAS")
            keyPassword = secret("ZAP_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release").takeIf { secret("ZAP_KEYSTORE") != null }
        }
    }

    // Same app, two packagings: "site" (download page; has a phone launcher icon) and
    // "nexus" (RokidBrew registry; no launcher icon, which the registry rejects).
    // Same id and key, so either installs over the other without losing data.
    flavorDimensions += "channel"
    productFlavors {
        create("site") { dimension = "channel" }
        create("nexus") { dimension = "channel" }
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
    // The real org.json on the JVM, so tests can run the SDK's own skills catalog parser.
    testImplementation("org.json:json:20240303")
}
