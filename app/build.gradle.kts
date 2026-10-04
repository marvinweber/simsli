import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// Simsli Server & Signing configuration — override in local.properties (never committed):
//
// Dev / Debug:
//   server.url=http://10.0.2.2:8080          (defaults to emulator -> host loopback)
//
// Release / Production:
//   server.release.url=https://api.simsli.app
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
    namespace = "net.marvinweber.simsli"
    // 37 required by material3 1.5.0-alpha29 (material3-ripple); see docs/adr/0013
    compileSdk = 37

    defaultConfig {
        applicationId = "net.marvinweber.simsli"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
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
        debug {
            buildConfigField(
                "String",
                "SERVER_URL",
                "\"${localProperties.getProperty("server.url") ?: "http://10.0.2.2:8080"}\""
            )
        }

        release {
            val serverReleaseUrl = localProperties.getProperty("server.release.url")
                ?: localProperties.getProperty("server.url")
                ?: System.getenv("SERVER_RELEASE_URL")
                ?: "https://api.simsli.app"
            buildConfigField("String", "SERVER_URL", "\"$serverReleaseUrl\"")

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
    implementation(libs.androidx.lifecycle.process)
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

    // HTTP & Networking
    implementation(libs.okhttp)

    // Coil
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Kotlinx
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
}

// ============================================================
// Version Automation Tasks
// ============================================================

fun bumpVersion(bumpCode: Boolean = true, majorBump: Boolean = false, minorBump: Boolean = false, patchBump: Boolean = false) {
    val props = Properties()
    if (versionPropsFile.exists()) {
        versionPropsFile.inputStream().use { props.load(it) }
    }
    val code = (props.getProperty("versionCode") ?: "1").toInt()
    val major = (props.getProperty("versionMajor") ?: "0").toInt()
    val minor = (props.getProperty("versionMinor") ?: "1").toInt()
    val patch = (props.getProperty("versionPatch") ?: "0").toInt()

    val nextCode = if (bumpCode) code + 1 else code
    val (nextMajor, nextMinor, nextPatch) = when {
        majorBump -> Triple(major + 1, 0, 0)
        minorBump -> Triple(major, minor + 1, 0)
        patchBump -> Triple(major, minor, patch + 1)
        else -> Triple(major, minor, patch)
    }

    props.setProperty("versionCode", nextCode.toString())
    props.setProperty("versionMajor", nextMajor.toString())
    props.setProperty("versionMinor", nextMinor.toString())
    props.setProperty("versionPatch", nextPatch.toString())
    versionPropsFile.outputStream().use {
        props.store(it, "Simsli Version Configuration")
    }

    println("🚀 Version updated: $major.$minor.$patch ($code) -> $nextMajor.$nextMinor.$nextPatch ($nextCode)")
}

tasks.register("bumpPatch") {
    group = "versioning"
    description = "Bumps patch version and versionCode (e.g. 0.1.0 (1) -> 0.1.1 (2))"
    doLast {
        bumpVersion(bumpCode = true, patchBump = true)
    }
}

tasks.register("bumpMinor") {
    group = "versioning"
    description = "Bumps minor version, resets patch to 0, and bumps versionCode (e.g. 0.1.1 (2) -> 0.2.0 (3))"
    doLast {
        bumpVersion(bumpCode = true, minorBump = true)
    }
}

tasks.register("bumpMajor") {
    group = "versioning"
    description = "Bumps major version, resets minor and patch to 0, and bumps versionCode (e.g. 0.2.0 (3) -> 1.0.0 (4))"
    doLast {
        bumpVersion(bumpCode = true, majorBump = true)
    }
}

tasks.register("bumpVersionCode") {
    group = "versioning"
    description = "Bumps only versionCode without changing versionName (e.g. 0.1.0 (1) -> 0.1.0 (2))"
    doLast {
        bumpVersion(bumpCode = true)
    }
}

tasks.register("currentVersion") {
    group = "versioning"
    description = "Displays the current app version and versionCode"
    doLast {
        val props = Properties()
        if (versionPropsFile.exists()) {
            versionPropsFile.inputStream().use { props.load(it) }
        }
        val code = props.getProperty("versionCode") ?: "1"
        val major = props.getProperty("versionMajor") ?: "0"
        val minor = props.getProperty("versionMinor") ?: "1"
        val patch = props.getProperty("versionPatch") ?: "0"
        println("Simsli Version: $major.$minor.$patch (versionCode: $code)")
    }
}