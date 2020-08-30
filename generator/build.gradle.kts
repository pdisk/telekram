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

val serializationVersion = "1.0.0-RC"

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization") version "1.4.0"
    application
}

repositories {
    mavenCentral()
    jcenter()
}

kotlin {
    jvm()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:$serializationVersion")
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
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:$serializationVersion")
            }
            dependsOn(commonMain)
        }
        jvm().compilations["test"].defaultSourceSet {
            dependsOn(commonTest)
        }
    }
}

tasks {
    getByName<Delete>("clean") {
        delete.add("../core/generated")
    }
    getByName<org.gradle.jvm.tasks.Jar>("jar") {
        manifest {
            attributes["Main-Class"] = "tk.hack5.telekram.generator.MainKt"
        }
    }
    getByName<JavaExec>("run") {
        dependsOn("jvmMainClasses")
        inputs.dir("resources")
            .withPropertyName("inputSchema")
            .withPathSensitivity(PathSensitivity.RELATIVE)
        outputs.dir("../core/generated")
        classpath =
            kotlin.jvm().compilations["main"].runtimeDependencyFiles + sourceSets["main"].runtimeClasspath + files("build/classes/kotlin/jvm/main")
    }
}

application {
    mainClassName = "tk.hack5.telekram.generator.MainKt"
}