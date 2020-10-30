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
    id("org.jetbrains.kotlin.plugin.serialization") version "1.4.0"
    id("org.jetbrains.dokka") version "0.10.0"
    `maven-publish`
}

group = (rootProject.group as String) + ".core"
version = rootProject.version

repositories {
    mavenCentral()
    jcenter()
    maven(url = "https://jitpack.io")
}

val coroutinesVersion = "1.4.0-M1"
val napierVersion = "1.4.0"
val ktorVersion = "1.4.0"
val kotlinxIoVersion = "0.1.16"
val ktMathVersion = "0.2.2"
val serializationVersion = "1.0.0"
val klockVersion = "1.12.1"
val kryptoVersion = "1.12.0"
val bouncyCastleVersion = "1.66"

kotlin {
    jvm()

    sourceSets {
        val commonMain by getting {
            kotlin.srcDir("generated/commonMain")
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
                implementation("com.github.aakira:napier:$napierVersion")
                implementation("io.github.gciatto:kt-math:$ktMathVersion")
                implementation("com.soywiz.korlibs.krypto:krypto:$kryptoVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-io:$kotlinxIoVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
                implementation("com.soywiz.korlibs.klock:klock:$klockVersion")
            }
        }
        val commonTest by getting {
            dependencies {
                kotlin("test")
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("io.ktor:ktor-network:$ktorVersion")
                implementation("org.bouncycastle:bcprov-jdk15on:$bouncyCastleVersion")
                implementation("org.bouncycastle:bcpkix-jdk15on:$bouncyCastleVersion")
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

tasks.filter { it.name.startsWith("compileKotlin") }.forEach {
    it.dependsOn(project(":generator").getTasksByName("run", false).first())
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
                        uri("${System.getenv("CI_API_V4_URL")}/projects/" +
                                "${System.getenv("CI_PROJECT_ID")}/packages/maven")
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
