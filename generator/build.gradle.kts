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

val serializationVersion = "1.0.0"

plugins {
    kotlin("multiplatform")
    kotlin( "plugin.serialization") version "1.4.0"
    application
}

group = rootProject.group
version = rootProject.version

repositories {
    mavenCentral()
    jcenter()
    maven("https://jitpack.io")
}

kotlin {
    jvm()

    sourceSets {
        val commonMain by getting {
            kotlin.srcDir("generated/commonMain")
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
                implementation("com.github.kotlinx.ast:parser-antlr-kotlin:c7dd6bbd54")
            }
        }
        val commonTest by getting {
            dependencies {
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("com.squareup:kotlinpoet:1.7.2")
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

tasks {
    getByName<Delete>("clean") {
        delete.add("../core/generated")
    }
    getByName<org.gradle.jvm.tasks.Jar>("jar") {
        manifest {
            attributes["Main-Class"] = "dev.hack5.telekram.generator.MainKt"
        }
    }
    getByName<JavaExec>("run") {
        dependsOn("jvmMainClasses")
        inputs.dir("resources")
            .withPropertyName("inputSchema")
            .withPathSensitivity(PathSensitivity.RELATIVE)
        outputs.dir("../core/generated")

        classpath =
            kotlin.jvm().compilations["main"].runtimeDependencyFiles +
                    files(getByName<ProcessResources>(kotlin.jvm().compilations["main"].processResourcesTaskName).destinationDir) +
                    files(kotlin.jvm().compilations["main"].compileKotlinTask.destinationDir)
    }

    register<com.strumenta.antlrkotlin.gradleplugin.AntlrKotlinTask>("generateKotlinCommonGrammarSource") {
        antlrClasspath = configurations.detachedConfiguration(
            project.dependencies.create("org.antlr:antlr4:4.7.1"),
            project.dependencies.create("com.github.drieks.antlr-kotlin:antlr-kotlin-target:ce5a7e161a")
        )
        maxHeapSize = "64m"
        packageName = "tk.hack5.telekram.generator.tl"
        arguments = listOf("-no-visitor")
        source = project.objects
            .sourceDirectorySet("commonMain", "commonMain")
            .srcDir("src/commonMain/antlr").apply {
                include("*.g4")
            }
        inputs.dir("src/commonMain/antlr")
            .withPropertyName("inputG4")
            .withPathSensitivity(PathSensitivity.RELATIVE)
        outputDirectory = File("generated/commonMain")
    }
    getByName("compileKotlinJvm").dependsOn("generateKotlinCommonGrammarSource")
}

application {
    mainClassName = "dev.hack5.telekram.generator.MainKt"
}