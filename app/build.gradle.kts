import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// Supabase & Signing configuration — override in local.properties (never committed):
//
// Dev / Debug:
//   supabase.url=http://10.0.2.2:54321       (defaults to emulator -> host loopback)
//   supabase.anon.key=eyJ...                 (local anon key)
//
// Release / Staging:
//   supabase.release.url=https://<ref>.supabase.co
//   supabase.release.anon.key=<cloud-anon-key>
//
// Release Signing (for CLI ./gradlew bundleRelease):
//   release.keystore.file=/path/to/upload-keystore.jks
//   release.keystore.password=secret
//   release.key.alias=upload-key
//   release.key.password=secret
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "net.marvinweber.simsli"
    // 37 required by material3 1.5.0-alpha29 (material3-ripple); see docs/adr/0013
    compileSdk = 37

    defaultConfig {
        applicationId = "net.marvinweber.simsli"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
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
        debug {
            buildConfigField(
                "String",
                "SUPABASE_URL",
                "\"${localProperties.getProperty("supabase.url") ?: "http://10.0.2.2:54321"}\""
            )
            buildConfigField(
                "String",
                "SUPABASE_ANON_KEY",
                "\"${localProperties.getProperty("supabase.anon.key") ?: ""}\""
            )
        }

        release {
            val releaseUrl = localProperties.getProperty("supabase.release.url")
                ?: localProperties.getProperty("supabase.url")
                ?: System.getenv("SUPABASE_RELEASE_URL")
                ?: ""
            val releaseKey = localProperties.getProperty("supabase.release.anon.key")
                ?: localProperties.getProperty("supabase.anon.key")
                ?: System.getenv("SUPABASE_RELEASE_ANON_KEY")
                ?: ""

            buildConfigField("String", "SUPABASE_URL", "\"$releaseUrl\"")
            buildConfigField("String", "SUPABASE_ANON_KEY", "\"$releaseKey\"")

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
        buildConfig = true
    }
}

dependencies {
    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.emoji2.emojipicker)

    // Compose BOM — import first, then add compose libs without versions
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // Navigation
    implementation(libs.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Supabase BOM — import first, then add supabase libs without versions
    val supabaseBom = platform(libs.supabase.bom)
    implementation(supabaseBom)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.realtime)
    implementation(libs.supabase.auth)
    implementation(libs.supabase.storage)

    // Ktor (required by supabase-kt)
    implementation(libs.ktor.client.okhttp)

    // Coil
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Kotlinx
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
}