plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.hypernotifyfix"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.hypernotifyfix"
        minSdk = 26
        targetSdk = 34
        versionCode = 25
        versionName = "0.10.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; aidl = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    lint { abortOnError = true; warningsAsErrors = false }
    buildTypes {
        release {
            // Keeps on-device test upgrades compatible with the existing installation while
            // producing a non-debuggable release artifact. Replace before store distribution.
            signingConfig = signingConfigs.getByName("debug")
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
