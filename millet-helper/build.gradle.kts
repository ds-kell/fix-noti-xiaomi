import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Release key: read from keystore.properties at the repo root (written by CI from secrets, never committed).
// Without it, release builds fall back to the debug key so local builds keep working.
val releaseKeystore = rootProject.file("keystore.properties").takeIf { it.exists() }
    ?.let { file -> Properties().apply { file.inputStream().use(::load) } }

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
    signingConfigs {
        releaseKeystore?.let { props -> create("release") {
            storeFile = rootProject.file(props.getProperty("storeFile"))
            storePassword = props.getProperty("storePassword")
            keyAlias = props.getProperty("keyAlias")
            keyPassword = props.getProperty("keyPassword")
        } }
    }
    buildTypes {
        release {
            // Must share the main app's certificate: the control provider is signature-protected.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation(project(":whitelist-core"))
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
