plugins { kotlin("jvm") version "2.2.10" }

group = "io.alema"
version = "0.1.0"

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }

dependencies { testImplementation(kotlin("test")) }

tasks.test { useJUnitPlatform() }
