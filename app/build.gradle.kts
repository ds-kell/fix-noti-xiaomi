import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release key: read from keystore.properties at the repo root (written by CI from secrets, never committed).
// Without it, release builds fall back to the debug key so local builds keep working.
val releaseKeystore = rootProject.file("keystore.properties").takeIf { it.exists() }
    ?.let { file -> Properties().apply { file.inputStream().use(::load) } }

android {
    namespace = "io.github.hypernotifyfix"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.hypernotifyfix"
        minSdk = 26
        targetSdk = 34
        versionCode = 29
        versionName = "0.11.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; aidl = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    lint { abortOnError = true; warningsAsErrors = false }
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
            // Release key from keystore.properties; the MILLET helper must use the same key.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            isDebuggable = false
            isMinifyEnabled = false
        }
    }
}

// The targetSdk 22 MILLET helper ships inside the main APK and is installed through Shizuku.
val helperAssetsDir = layout.buildDirectory.dir("generated/helperAssets")
val copyMilletHelper by tasks.registering(Copy::class) {
    dependsOn(":millet-helper:assembleRelease")
    from(project(":millet-helper").layout.buildDirectory.file("outputs/apk/release/millet-helper-release.apk"))
    into(helperAssetsDir)
    rename { "millet-helper.apk" }
}
android.sourceSets["main"].assets.srcDir(helperAssetsDir)
tasks.named("preBuild") { dependsOn(copyMilletHelper) }

dependencies {
    implementation(project(":whitelist-core"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
