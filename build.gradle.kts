import java.util.Properties

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}

// ============================================================
// Version Automation Tasks
// ============================================================

val versionPropsFile = layout.projectDirectory.file("version.properties").asFile
val serverConfigFile = layout.projectDirectory.file("server/internal/config/config.go").asFile
val serverDockerFile = layout.projectDirectory.file("server/Dockerfile").asFile

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

    val newVersionName = "$nextMajor.$nextMinor.$nextPatch"

    // Update server/internal/config/config.go
    if (serverConfigFile.exists()) {
        val content = serverConfigFile.readText()
        val updated = content.replace(Regex("""var Version = "[^"]*""""), """var Version = "$newVersionName"""")
        if (updated != content) {
            serverConfigFile.writeText(updated)
            println("📦 Updated server config version to $newVersionName")
        }
    }

    // Update server/Dockerfile
    if (serverDockerFile.exists()) {
        val content = serverDockerFile.readText()
        val updated = content.replace(Regex("""ARG VERSION=[^\s\n]+"""), """ARG VERSION=$newVersionName""")
        if (updated != content) {
            serverDockerFile.writeText(updated)
            println("🐳 Updated server Dockerfile version to $newVersionName")
        }
    }

    println("🚀 Version updated: $major.$minor.$patch ($code) -> $newVersionName ($nextCode)")
}

tasks.register("bumpPatch") {
    group = "versioning"
    description = "Bumps patch version and versionCode across app and server (e.g. 0.1.0 (1) -> 0.1.1 (2))"
    doLast {
        bumpVersion(bumpCode = true, patchBump = true)
    }
}

tasks.register("bumpMinor") {
    group = "versioning"
    description = "Bumps minor version, resets patch to 0, and bumps versionCode across app and server (e.g. 0.1.1 (2) -> 0.2.0 (3))"
    doLast {
        bumpVersion(bumpCode = true, minorBump = true)
    }
}

tasks.register("bumpMajor") {
    group = "versioning"
    description = "Bumps major version, resets minor and patch to 0, and bumps versionCode across app and server (e.g. 0.2.0 (3) -> 1.0.0 (4))"
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
    description = "Displays the current version across app and server"
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