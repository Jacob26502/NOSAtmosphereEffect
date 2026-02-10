plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.app.nosatmosphereeffect"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.saad_khan_rind.atmosphere_effect"
        val targetSdkEnv = project.findProperty("targetSdkOverride")?.toString()?.toIntOrNull() ?: 36
        targetSdk = targetSdkEnv
        minSdk = if (targetSdkEnv >= 36) 36 else 33
        val baseVersionCode = 26
        val codeOffset = if (targetSdkEnv >= 36) 200000 else 100000
        versionCode = baseVersionCode + codeOffset
        versionName = "4.4.3"
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
        }

        getByName("release") {
            isMinifyEnabled = false
        }
    }

    buildToolsVersion = "35.0.0"
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.exifinterface)
    // Versions
    val targetSdkEnv = project.findProperty("targetSdkOverride")?.toString()?.toIntOrNull() ?: 36

    val coreKtxVersion: String
    val lifecycleVersion: String
    val appcompatVersion: String
    val materialVersion: String

    if (targetSdkEnv >= 36) {
        coreKtxVersion = "1.17.0"
        lifecycleVersion = "2.10.0"
        appcompatVersion = "1.7.1"
        materialVersion = "1.13.0"
    } else {
        coreKtxVersion = "1.12.0"
        lifecycleVersion = "2.6.2"
        appcompatVersion = "1.6.1"
        materialVersion = "1.11.0"
    }

    // Core Android
    implementation("androidx.core:core-ktx:$coreKtxVersion")
    implementation("androidx.lifecycle:lifecycle-service:$lifecycleVersion")

    implementation("androidx.appcompat:appcompat:$appcompatVersion")
    implementation("com.google.android.material:material:$materialVersion")
}