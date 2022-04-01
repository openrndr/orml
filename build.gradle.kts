import java.net.URI

plugins {
    kotlin("jvm") version Versions.kotlin apply false
    `maven-publish`
    java
    signing
    id("io.github.gradle-nexus.publish-plugin") version "1.1.0"
}
publishing {
    publications {
        create<MavenPublication>("orml") {
            from(components["java"])
            groupId = "org.openrndr"
        }
    }
}

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("com.netflix.nebula:nebula-release-plugin:16.0.0")
    }
}

val isReleaseVersion = !project.version.toString().endsWith("SNAPSHOT")

// we still have some modules that are built using Groovy script
extra["orxTensorflowBackend"] = "orx-tensorflow"
extra["openrndrOS"] = Configuration.openrndrOS
extra["openrndrVersion"] = System.getenv("OPENRNDR_VERSION")?.replaceFirst("v", "") ?: Versions.openrndr
extra["orxVersion"] = System.getenv("ORX_VERSION")?.replaceFirst("v", "") ?: Versions.orx

val publishableProjects = listOf(
    "orml",
    "orml-blazepose",
    "orml-bodypix",
    "orml-dbface",
    "orml-facemesh",
    "orml-image-classsifier",
    "orml-ssd",
    "orml-style-transfer",
    "orml-super-resolution",
    "orml-u2net",
    "orml-utils"
)
allprojects {
    apply {
        plugin("java")
        plugin("kotlin")
        plugin("nebula.release")
        plugin("maven-publish")
        plugin("signing")
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

publishing {
    repositories {
        maven {
            credentials {
                username = findProperty("ossrhUsername")?.toString() ?: System.getenv("OSSRH_USERNAME")
                password = findProperty("ossrhPassword")?.toString() ?: System.getenv("OSSRH_PASSWORD")
            }
            url = if (!isReleaseVersion) {
                URI("https://s01.oss.sonatype.org/content/repositories/snapshots")
            } else {
                URI("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2")
            }
        }
    }
}

allprojects.filter { it.name in publishableProjects }.forEach {
    val fjdj = it.tasks.create("fakeJavaDocJar", Jar::class) {
        archiveClassifier.set("javadoc")
    }
    publishing {
        publications {
            matching { it.name == "jvm" }.forEach { publication ->
                publication as MavenPublication
                publication.artifact(fjdj)
            }
        }
        publications {
            all {
                this as MavenPublication
                this.pom {
                    name.set("${project.name}")
                    url.set("https://orml.openrndr.org")
                    developers {
                        developer {
                            id.set("edwinjakobs")
                            name.set("Edwin Jakobs")
                            email.set("edwin@openrndr.org")
                        }
                    }
                    licenses {
                        license {
                            name.set("BSD-2-Clause")
                            url.set("https://github.com/openrndr/openrndr/blob/master/LICENSE")
                            distribution.set("repo")
                        }
                    }
                }
            }
        }
    }
}

signing {
    this.setRequired({ isReleaseVersion && gradle.taskGraph.hasTask("publish") })
    sign(publishing.publications)
}

nexusPublishing {
    repositories {
        sonatype {
            username.set(findProperty("ossrhUsername")?.toString() ?: System.getenv("OSSRH_USERNAME"))
            password.set(findProperty("ossrhPassword")?.toString() ?: System.getenv("OSSRH_PASSWORD"))
            nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
            snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots"))
        }
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
