import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.compose")
    kotlin("plugin.serialization")
}

// A fixed signing key, so every CI build installs as an update over the previous one
// (keeping your plan and history). It only signs this personal, sideloaded app.
// To use a private key instead, set KEYSTORE_FILE / KEYSTORE_PASSWORD in the environment.
val keystore = System.getenv("KEYSTORE_FILE")?.let { file(it) } ?: file("signing/gymhelper.jks")
val keystorePassword = System.getenv("KEYSTORE_PASSWORD") ?: "gymhelper"

android {
    namespace = "gymhelper.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.gilad905.gymhelper"
        minSdk = 26
        targetSdk = 36
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "0.1." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
    }

    signingConfigs {
        create("app") {
            storeFile = keystore
            storePassword = keystorePassword
            keyAlias = "gymhelper"
            keyPassword = keystorePassword
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("app")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":core"))
    implementation(platform("androidx.compose:compose-bom:2025.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    implementation("androidx.lifecycle:lifecycle-service:2.9.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
