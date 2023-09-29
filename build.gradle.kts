import org.jetbrains.compose.desktop.application.dsl.TargetFormat

val ktorVersion: String by project

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose") version "1.5.10-beta02"
}

kotlin {
    jvm {
        withJava()
    }

    val osName = System.getProperty("os.name")
    val targetOs = when {
        osName == "Mac OS X" -> "macos"
        osName.startsWith("Win") -> "windows"
        osName.startsWith("Linux") -> "linux"
        else -> error("Unsupported OS: $osName")
    }

    val targetArch = when (val osArch = System.getProperty("os.arch")) {
        "x86_64", "amd64" -> "x64"
        "aarch64" -> "arm64"
        else -> error("Unsupported arch: $osArch")
    }

    val version = "0.7.70" // or any more recent version
    val target = "${targetOs}-${targetArch}"

    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(compose.desktop.windows_x64)
                implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.5")
                implementation("io.ktor:ktor-client-core:$ktorVersion")
//                implementation("io.ktor:ktor-client-cio:$ktor_version")
                implementation("io.ktor:ktor-client-okhttp:$ktorVersion")
                implementation("org.slf4j:slf4j-nop:1.7.32")
//                implementation("org.jetbrains.skiko:skiko-awt-runtime-$target:$version")
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "compose-ui-desktop"
            packageVersion = "1.0.0"
        }
    }
}


repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

group = "com.shish"
version = "1.0-SNAPSHOT"
//dependencies {
//    implementation("io.ktor:ktor-client-okhttp-jvm:2.3.4")
//}
