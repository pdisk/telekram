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
    application
}

repositories {
    mavenCentral()
    jcenter()
}

val coroutinesVersion = "1.4.0-M1"
val napierVersion = "1.4.0"

kotlin {
    jvm()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":api"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
                implementation("com.github.aakira:napier:$napierVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-debug:$coroutinesVersion")
            }
        }
        val commonTest by getting {
            dependencies {
            }
        }
        jvm().compilations["main"].defaultSourceSet {
            dependencies {
            }
        }
        jvm().compilations["test"].defaultSourceSet {
        }
    }
}

tasks {
    getByName<org.gradle.jvm.tasks.Jar>("jar") {
        manifest {
            attributes["Main-Class"] = "dev.hack5.telekram.sample.MainKt"
        }
    }
    getByName<JavaExec>("run") {
        dependsOn("jvmMainClasses")
        classpath =
            kotlin.jvm().compilations["main"].runtimeDependencyFiles + sourceSets["main"].runtimeClasspath + files("build/classes/kotlin/jvm/main")
    }
}

application {
    mainClassName = "dev.hack5.telekram.sample.MainKt"
}