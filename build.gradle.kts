plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}

// One fixed key for every Android build, local and CI, so a new APK always installs over the old one
// (the app and the MILLET helper must also share it). Git-ignored keystore.properties (CI writes it from secrets), overridable per
// field with HYPERNOTIFY_SIGNING_* env vars; if incomplete, each machine's own debug key as before.
val fixedSigning = java.util.Properties()
    .apply { rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use(::load) }
    .let { props -> listOf("storeFile", "storePassword", "keyAlias", "keyPassword").associateWith { props.getProperty(it) ?: System.getenv("HYPERNOTIFY_SIGNING_${it.uppercase()}") } }
    .takeIf { values -> values.values.none { it.isNullOrBlank() } }
    ?.mapValues { it.value!! }

subprojects {
    plugins.withId("com.android.application") {
        val signing = fixedSigning ?: return@withId
        // Overriding the debug config covers debug builds and the release builds that already point at it.
        extensions.configure<com.android.build.api.dsl.ApplicationExtension> {
            signingConfigs.getByName("debug") {
                storeFile = rootProject.file(signing.getValue("storeFile"))
                storePassword = signing.getValue("storePassword")
                keyAlias = signing.getValue("keyAlias")
                keyPassword = signing.getValue("keyPassword")
            }
        }
    }
}
