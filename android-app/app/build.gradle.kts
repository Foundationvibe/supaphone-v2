import java.util.Properties
import org.gradle.api.GradleException

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services) apply false
}

if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

fun localProp(name: String, fallback: String = ""): String =
    localProperties.getProperty(name, fallback).trim()

fun asBuildConfigString(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

val sampleAdMobAppId = "ca-app-pub-3940256099942544~3347511713"
val sampleAppOpenAdUnitId = "ca-app-pub-3940256099942544/9257395921"
val sampleBannerAdUnitId = "ca-app-pub-3940256099942544/9214589741"
val configuredAdMobAppId = localProp("ADMOB_APP_ID")
val configuredAppOpenAdUnitId = localProp("ADMOB_APP_OPEN_AD_UNIT_ID")
val configuredBannerAdUnitId = localProp("ADMOB_BANNER_AD_UNIT_ID")
val isReleaseTaskRequested = gradle.startParameter.taskNames.any { taskName ->
    val normalized = taskName.lowercase()
    normalized.contains("release") || normalized.contains("publish")
}

fun isGoogleSampleAdId(value: String): Boolean =
    value.startsWith("ca-app-pub-3940256099942544")

fun requireProductionAdId(name: String, value: String) {
    if (value.isBlank()) {
        throw GradleException("Release builds require $name in android-app/local.properties.")
    }
    if (isGoogleSampleAdId(value)) {
        throw GradleException("Release builds cannot use Google sample ad IDs for $name.")
    }
}

android {
    namespace = "com.supaphone.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.supaphone.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SUPABASE_URL", asBuildConfigString(localProp("SUPABASE_URL")))
        buildConfigField("String", "SUPABASE_ANON_KEY", asBuildConfigString(localProp("SUPABASE_ANON_KEY")))
        buildConfigField("String", "SUPAPHONE_EDGE_BASE_URL", asBuildConfigString(localProp("SUPAPHONE_EDGE_BASE_URL")))
        buildConfigField(
            "String",
            "SUPAPHONE_WEBSITE_BASE_URL",
            asBuildConfigString(localProp("SUPAPHONE_WEBSITE_BASE_URL", "https://foundationvibe.github.io/supaphone-v2"))
        )
        buildConfigField("String", "ADMOB_APP_ID", asBuildConfigString(""))
        buildConfigField("String", "ADMOB_APP_OPEN_AD_UNIT_ID", asBuildConfigString(""))
        buildConfigField("String", "ADMOB_BANNER_AD_UNIT_ID", asBuildConfigString(""))
        buildConfigField("boolean", "ALLOW_UNOFFICIAL_WHATSAPP_PACKAGES", "false")

        manifestPlaceholders["ADMOB_APP_ID"] = ""
    }

    flavorDimensions += "distribution"

    productFlavors {
        create("play") {
            dimension = "distribution"
            buildConfigField("boolean", "ALLOW_UNOFFICIAL_WHATSAPP_PACKAGES", "false")
        }
        create("direct") {
            dimension = "distribution"
            buildConfigField("boolean", "ALLOW_UNOFFICIAL_WHATSAPP_PACKAGES", "true")
        }
    }

    buildTypes {
        debug {
            val debugAdMobAppId = configuredAdMobAppId.ifBlank { sampleAdMobAppId }
            val debugAppOpenAdUnitId = configuredAppOpenAdUnitId.ifBlank { sampleAppOpenAdUnitId }
            val debugBannerAdUnitId = configuredBannerAdUnitId.ifBlank { sampleBannerAdUnitId }

            buildConfigField("String", "ADMOB_APP_ID", asBuildConfigString(debugAdMobAppId))
            buildConfigField("String", "ADMOB_APP_OPEN_AD_UNIT_ID", asBuildConfigString(debugAppOpenAdUnitId))
            buildConfigField("String", "ADMOB_BANNER_AD_UNIT_ID", asBuildConfigString(debugBannerAdUnitId))
            manifestPlaceholders["ADMOB_APP_ID"] = debugAdMobAppId
        }
        release {
            if (isReleaseTaskRequested) {
                requireProductionAdId("ADMOB_APP_ID", configuredAdMobAppId)
                requireProductionAdId("ADMOB_APP_OPEN_AD_UNIT_ID", configuredAppOpenAdUnitId)
                requireProductionAdId("ADMOB_BANNER_AD_UNIT_ID", configuredBannerAdUnitId)
            }

            buildConfigField("String", "ADMOB_APP_ID", asBuildConfigString(configuredAdMobAppId))
            buildConfigField("String", "ADMOB_APP_OPEN_AD_UNIT_ID", asBuildConfigString(configuredAppOpenAdUnitId))
            buildConfigField("String", "ADMOB_BANNER_AD_UNIT_ID", asBuildConfigString(configuredBannerAdUnitId))
            manifestPlaceholders["ADMOB_APP_ID"] = configuredAdMobAppId
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Security - EncryptedSharedPreferences
    implementation(libs.androidx.security.crypto)

    // CameraX (for QR scanning)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // ML Kit Barcode Scanning
    implementation(libs.mlkit.barcode.scanning)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    implementation(libs.libphonenumber)
    implementation("com.google.guava:guava:33.4.0-android")
    implementation(libs.google.mobile.ads)
    implementation(libs.google.ump)

    // SVG logo rendering
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
}
