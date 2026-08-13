import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.moments.android"
    compileSdk = 37

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    defaultConfig {
        // Play package (irreversible). Code namespace stays com.moments.android.
        applicationId = "com.momentsapp"
        minSdk = 26
        targetSdk = 37
        // Subir versionCode en cada AAB que subas a Play.
        versionCode = 7
        versionName = "1.0.6"
        // Snap Camera Kit ≡ iOS Info.plist SCCameraKit* + SnapCameraKit.plist (vacío = no configurado).
        buildConfigField("String", "SC_CAMERA_KIT_API_TOKEN", "\"\"")
        buildConfigField("String", "SC_CAMERA_KIT_CLIENT_ID", "\"\"")
        buildConfigField("String", "SC_CAMERA_KIT_LENS_GROUP_ID", "\"6249bcf5-6bb2-4845-9021-0a6c5464963f\"")

        // Secrets desde local.properties (gitignored).
        val localProps = Properties().apply {
            val f = rootProject.file("local.properties")
            if (f.exists()) f.inputStream().use { load(it) }
        }

        // Google Places / Maps — GOOGLE_MAPS_API_KEY=AIza… (Places API New + Geocoding).
        val googleMapsKey = localProps.getProperty("GOOGLE_MAPS_API_KEY")
            ?.trim()
            ?.takeIf { it.isNotEmpty() && !it.startsWith("REPLACE_") && it != "YOUR_GOOGLE_MAPS_API_KEY" }
            ?: "REPLACE_WHEN_YOU_HAVE_GOOGLE_KEY"
        buildConfigField("String", "GOOGLE_MAPS_API_KEY", "\"${googleMapsKey.replace("\"", "\\\"")}\"")
        manifestPlaceholders["GOOGLE_MAPS_API_KEY"] = googleMapsKey

        // Mapbox public token — MAPBOX_ACCESS_TOKEN=pk.…
        val mapboxToken = localProps.getProperty("MAPBOX_ACCESS_TOKEN")
            ?.trim()
            ?.takeIf { it.isNotEmpty() && it != "YOUR_MAPBOX_ACCESS_TOKEN" }
            ?: "YOUR_MAPBOX_ACCESS_TOKEN"
        buildConfigField("String", "MAPBOX_ACCESS_TOKEN", "\"${mapboxToken.replace("\"", "\\\"")}\"")

        // OpenWeather Current API 2.5 — OPENWEATHER_API_KEY=…
        val openWeatherKey = localProps.getProperty("OPENWEATHER_API_KEY")
            ?.trim()
            ?.takeIf { it.isNotEmpty() && it != "YOUR_OPENWEATHER_API_KEY" }
            ?: "YOUR_OPENWEATHER_API_KEY"
        buildConfigField("String", "OPENWEATHER_API_KEY", "\"${openWeatherKey.replace("\"", "\\\"")}\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Firma release: keystore.properties (gitignored). Sin ese fichero, release usa debug
    // solo para builds locales — Play Store requiere el upload keystore.
    val keystorePropsFile = rootProject.file("keystore.properties")
    val keystoreProps = Properties().takeIf { keystorePropsFile.exists() }?.apply {
        FileInputStream(keystorePropsFile).use { load(it) }
    }
    signingConfigs {
        create("release") {
            if (keystoreProps != null) {
                val storePath = keystoreProps.getProperty("storeFile")
                    ?: error("keystore.properties: falta storeFile")
                storeFile = rootProject.file(storePath)
                storePassword = keystoreProps.getProperty("storePassword")
                    ?: error("keystore.properties: falta storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                    ?: error("keystore.properties: falta keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
                    ?: error("keystore.properties: falta keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (keystoreProps != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    // Navigation 3 (migración incremental — skill navigation-3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material3.adaptive.navigation.suite)
    implementation(libs.androidx.window)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.material.icons.extended)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.functions)
    implementation(libs.firebase.appcheck)
    implementation(libs.firebase.appcheck.play.integrity)
    implementation(libs.firebase.appcheck.debug)
    implementation(libs.firebase.ai)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.media3.effect)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.video)
    implementation(libs.bouncycastle.bcprov)
    implementation(libs.play.services.ads)
    implementation(libs.user.messaging.platform)
    implementation(libs.google.maps.compose)
    implementation(libs.google.places)
    implementation(libs.mapbox.maps)
    implementation(libs.mapbox.maps.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.zxing.core)
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation("junit:junit:4.13.2")
}
