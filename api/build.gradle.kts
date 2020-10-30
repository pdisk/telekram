import org.jetbrains.kotlin.konan.properties.loadProperties

/*
 *     This file is part of Telekram (Telegram MTProto client library)
 *     Copyright (C) 2020 Hackintosh Five
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Lesser General Public License as
 *     published by the Free Software Foundation, either version 3 of the
 *     License, or (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Lesser General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.dokka") version "0.10.0"
    id("maven-publish")
}

group = "dev.hack5.telekram"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
    jcenter()
    maven(url = "https://jitpack.io")
}

val coroutinesVersion = "1.4.0-M1"
val napierVersion = "1.4.0"

kotlin {
    jvm()

    sourceSets {
        val commonMain by getting {
            kotlin.srcDir("generated/commonMain")
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
                implementation("com.github.aakira:napier:$napierVersion")
                api(project(":core"))
            }
        }
        val commonTest by getting {
            dependencies {
            }
        }
        val jvmMain by getting {
            dependencies {
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-junit"))
            }
        }
    }
}

tasks.dokka {
    outputFormat = "html"
    outputDirectory = "$buildDir/javadoc"
}

val dokkaJar by tasks.creating(Jar::class) {
    group = JavaBasePlugin.DOCUMENTATION_GROUP
    description = "Assembles Kotlin docs with Dokka"
    archiveClassifier.set("javadoc")
    from(tasks.dokka)
}

publishing {
    repositories {
        try {
            if (System.getenv("CI_API_V4_URL") != null) {
                maven {
                    url =
                        uri(System.getenv("CI_API_V4_URL") + "/")
                            .resolve("projects")
                            .resolve(System.getenv("CI_PROJECT_ID"))
                            .resolve("packages")
                            .resolve("maven")
                    name = "GitLab"
                    credentials(HttpHeaderCredentials::class) {
                        name = "Job-Token"
                        value = System.getenv("CI_JOB_TOKEN")
                    }
                    authentication {
                        register("GitLab", HttpHeaderAuthentication::class.java)
                    }
                }
            }
        } catch (e: NoSuchFileException) {
            mavenLocal()
        }
    }
}
