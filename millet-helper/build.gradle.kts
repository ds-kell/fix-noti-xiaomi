plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "io.github.hypernotifyfix.millethelper"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.hypernotifyfix.millethelper"
        minSdk = 26
        // Intentional: SettingsProvider only lets apps targeting < 23 write non-public
        // Settings.System keys such as MILLET_NO_RESTRICT_APP with WRITE_SETTINGS.
        // Keep this module tiny; the main app stays on a modern targetSdk.
        @Suppress("ExpiredTargetSdkVersion")
        targetSdk = 22
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    lint { checkReleaseBuilds = false; disable += setOf("ExpiredTargetSdkVersion", "OldTargetApi") }
    buildTypes {
        release {
            // Must share the main app's certificate: the control provider is signature-protected.
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation(project(":whitelist-core"))
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
