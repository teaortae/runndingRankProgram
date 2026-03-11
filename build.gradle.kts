import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
}

kotlin {
    jvmToolchain(17)
}

compose.desktop {
    application {
        mainClass = "app.MainKt"

        nativeDistributions {
            targetFormats(
                TargetFormat.Dmg,
                TargetFormat.Exe,
                TargetFormat.Msi,
            )
            packageName = "RunnerSheetInput"
            packageVersion = "1.0.0"
            description = "Google Sheets runner tracking desktop app"
            vendor = "Taehwan"

            windows {
                dirChooser = true
                perUserInstall = true
                menuGroup = "Runner Sheet Input"
                upgradeUuid = "84d5cb25-3b07-4961-8087-8f23cb5ebc25"
                exePackageVersion = "1.0.0"
                msiPackageVersion = "1.0.0"
            }
        }
    }
}
