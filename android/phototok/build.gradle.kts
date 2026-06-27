plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.phototok"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.phototok"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "0.4.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters.addAll(setOf("arm64-v8a", "x86_64"))
        }
    }

    signingConfigs {
        create("release") {
            // App-scoped env vars so each app uses its own upload key (full isolation).
            val keystorePath = System.getenv("PHOTOTOK_KEYSTORE_FILE")
            if (!keystorePath.isNullOrEmpty()) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("PHOTOTOK_STORE_PASSWORD")
                keyAlias = System.getenv("PHOTOTOK_KEY_ALIAS")
                keyPassword = System.getenv("PHOTOTOK_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            val keystorePath = System.getenv("PHOTOTOK_KEYSTORE_FILE")
            if (!keystorePath.isNullOrEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    // AndroidX Core
    implementation(libs.androidx.core.ktx)

    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.window.size)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Activity
    implementation(libs.androidx.activity.compose)

    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // ExifInterface
    implementation(libs.androidx.exifinterface)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // DocumentFile
    implementation(libs.androidx.documentfile)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Google Play Services (Google Sign-In for Drive)
    implementation(libs.play.services.auth)
    implementation(libs.kotlinx.coroutines.play.services)


    // Coil (image loading)
    implementation(libs.coil.compose)

    // Testing
    testImplementation(libs.junit)
}
