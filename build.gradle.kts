import Orml_markdown_to_jekyll_gradle.MarkdownToJekyllTask

plugins {
    kotlin("jvm") version "1.5.21" apply false
}

val orxUseSnapshot = false
val openrndrUseSnapshot = false

extra["orxVersion"] = if (orxUseSnapshot) "0.4.0-SNAPSHOT" else "0.3.58"

// choices are "orx-tensorflow-gpu", "orx-tensorflow-mkl", "orx-tensorflow"
extra["orxTensorflowBackend"] = "orx-tensorflow"

val supportedPlatforms = setOf("windows", "macos", "linux-x64", "linux-arm64")
val openrndrOS = if (project.hasProperty("targetPlatform")) {
    val platform : String = project.property("targetPlatform") as String
    if (platform !in supportedPlatforms) {
        error("target platform not supported: $platform")
    } else {
        platform
    }
} else when (org.gradle.internal.os.OperatingSystem.current()) {
    org.gradle.internal.os.OperatingSystem.WINDOWS -> "windows"
    org.gradle.internal.os.OperatingSystem.MAC_OS -> "macos"
    org.gradle.internal.os.OperatingSystem.LINUX -> when(val h = org.gradle.nativeplatform.platform.internal.DefaultNativePlatform(
        "current"
    ).architecture.name) {
        "x86-64" -> "linux-x64"
        "aarch64" -> "linux-arm64"
        else -> error("architecture not supported: $h")
    }
    else -> error("os not supported")
}

extra["openrndrOS"] = openrndrOS
extra["openrndrVersion"] = if (openrndrUseSnapshot) "0.4.0-SNAPSHOT" else "0.3.58"

allprojects {
    apply {
        plugin("kotlin")
    }
    repositories {
        mavenCentral()
    }
}

val markdownToJekyll = tasks.register<MarkdownToJekyllTask>("markdownToJekyll") {
    inputDir.set(file("$projectDir"))
    outputDir.set(file("docs"))
    ignore.set(listOf("demo-data", "orml-biggan", "orml-psenet", "orml-ssd", "orml-utils", "images"))
}.get()

//tasks.getByName("compileKotlinJvm").dependsOn(embedShaders)
//tasks.getByName("compileKotlinJs").dependsOn(embedShaders)