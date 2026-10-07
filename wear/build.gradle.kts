import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

val versionPropsFile = rootProject.file("version.properties")
val versionProps = Properties().apply {
    if (versionPropsFile.exists()) {
        versionPropsFile.inputStream().use { load(it) }
    }
}
val appVersionCode = (versionProps.getProperty("versionCode") ?: "1").toInt()
val appVersionMajor = (versionProps.getProperty("versionMajor") ?: "0").toInt()
val appVersionMinor = (versionProps.getProperty("versionMinor") ?: "1").toInt()
val appVersionPatch = (versionProps.getProperty("versionPatch") ?: "0").toInt()
val appVersionName = "$appVersionMajor.$appVersionMinor.$appVersionPatch"

android {
    namespace = "net.marvinweber.simsli.wear"
    compileSdk = 36

    defaultConfig {
        applicationId = "net.marvinweber.simsli"
        minSdk = 30
        targetSdk = 35
        // Google Play multi-device version code standard:
        // Wear OS range: 20_000_000 + base versionCode
        versionCode = 20_000_000 + appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        create("release") {
            val keystorePath = localProperties.getProperty("release.keystore.file")
                ?: System.getenv("RELEASE_KEYSTORE_FILE")
            if (!keystorePath.isNullOrBlank()) {
                val keystoreFile = rootProject.file(keystorePath)
                if (keystoreFile.exists()) {
                    storeFile = keystoreFile
                    storePassword = localProperties.getProperty("release.keystore.password")
                        ?: System.getenv("RELEASE_KEYSTORE_PASSWORD")
                    keyAlias = localProperties.getProperty("release.key.alias")
                        ?: System.getenv("RELEASE_KEY_ALIAS")
                    keyPassword = localProperties.getProperty("release.key.password")
                        ?: System.getenv("RELEASE_KEY_PASSWORD")
                }
            }
        }
    }

    buildTypes {
        release {
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null) {
                signingConfig = releaseSigning
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // AndroidX Core & Lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Compose Core
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    // Wear OS Compose & Play Services
    implementation(libs.androidx.wear.compose.material3)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.wear.compose.navigation)
    implementation(libs.play.services.wearable)

    // Wear OS Tiles & ProtoLayout
    implementation(libs.androidx.wear.tiles)
    implementation(libs.androidx.wear.protolayout)
    implementation(libs.androidx.wear.protolayout.material)
    implementation(libs.androidx.wear.protolayout.expression)
    implementation(libs.androidx.concurrent.futures)

    // Kotlinx
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)

    // Wear Common
    implementation(project(":wear-common"))
}
