plugins {
    kotlin("jvm") version Versions.kotlin apply false
    `maven-publish`
    java
}
//publishing {
//    publications {
//        create<MavenPublication>("orml") {
//            from(components["java"])
//            groupId = "org.openrndr"
//        }
//    }
//}

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("com.netflix.nebula:nebula-release-plugin:16.0.0")
    }
}

// we still have some modules that are built using Groovy script
extra["orxTensorflowBackend"] = "orx-tensorflow"
extra["openrndrOS"] = Configuration.openrndrOS
extra["openrndrVersion"] = Versions.openrndr
extra["orxVersion"] = Versions.orx



allprojects {




    apply {
        plugin("java")
        plugin("kotlin")
        plugin("nebula.release")
        plugin("maven-publish")
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    extensions.configure<PublishingExtension>() {
        this.publications {
            create<MavenPublication>("maven") {
                groupId = "org.openrndr.orml"
                from(components["java"])
            }
        }
    }

    repositories {
        mavenCentral()
        if (Versions.openrndrUseSnapshot || Versions.orxUseSnapshot) {
            mavenLocal()
        }
    }
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
    }
}


val markdownToJekyll = tasks.register<MarkdownToJekyllTask>("markdownToJekyll") {
    inputDir.set(file("$projectDir"))
    outputDir.set(file("docs"))
    ignore.set(
        listOf(
            "demo-data",
            "orml-biggan",
            "orml-psenet",
            "orml-ssd",
            "orml-utils",
            "images",
            "orml-mobile-stylegan"
        )
    )
    titles.set(mapOf("orml" to "Introduction to ORML"))
}.get()
