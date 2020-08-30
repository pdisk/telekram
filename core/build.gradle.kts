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

group = "tk.hack5.telekram"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
    jcenter()
    maven(url = "https://jitpack.io")
}

val coroutinesVersion = "1.3.9"
val napierVersion = "1.4.0"
val ktorVersion = "1.4.0"
val kotlinxIoVersion = "0.1.16"
val ktMathVersion = "0.0.6"
val serializationVersion = "1.0.0-RC"
val klockVersion = "1.12.0"
val kryptoVersion = "1.11.2"
val bouncyCastleVersion = "1.66"

kotlin {
    jvm()

    sourceSets {
        val commonMain by getting {
            kotlin.srcDir("generated/commonMain")
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
                implementation("com.github.aakira:napier:$napierVersion")
                implementation("io.github.gciatto:kt-math-metadata:$ktMathVersion")
                implementation("com.soywiz.korlibs.krypto:krypto:$kryptoVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-io:$kotlinxIoVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:$serializationVersion")
                implementation("com.soywiz.korlibs.klock:klock:$klockVersion")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
        jvm().compilations["main"].defaultSourceSet {
            dependencies {
                implementation(kotlin("stdlib-jdk8"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
                implementation("com.github.aakira:napier-jvm:$napierVersion")
                implementation("io.ktor:ktor-network:$ktorVersion")
                implementation("io.github.gciatto:kt-math-jvm:$ktMathVersion")
                implementation("com.soywiz.korlibs.krypto:krypto-jvm:$kryptoVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-io-jvm:$kotlinxIoVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:$serializationVersion")
                implementation("com.soywiz.korlibs.klock:klock-jvm:$klockVersion")
                implementation("org.bouncycastle:bcprov-jdk15on:$bouncyCastleVersion")
                implementation("org.bouncycastle:bcpkix-jdk15on:$bouncyCastleVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-debug:$coroutinesVersion")
            }
            dependsOn(commonMain)
        }
        jvm().compilations["test"].defaultSourceSet {
            dependencies {
                implementation(kotlin("test-junit"))
                implementation("org.junit.jupiter:junit-jupiter:5.5.2")
            }
            dependsOn(commonTest)
        }
    }
}

tasks.filter { it.name.startsWith("compileKotlin") }.forEach {
    //it.dependsOn(project(":generator").getTasksByName("run", false).first())
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
        mavenLocal()
    }
}