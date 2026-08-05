plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

// :core holds the *only* code both Android products are allowed to share.
//
// Admission rule — a file belongs here when it is identical for Android Desktop
// (:android-desktop) and PhotoTok (:phototok) and would stay identical if either product
// changed independently. Anything that merely looks similar today stays
// duplicated in the products on purpose: the two apps are independent
// solutions, and premature sharing is what couples them.
//
// :core must not depend on Compose, Room, OpenCV, Vico or on either app module.
android {
    namespace = "com.photoselector.core"
    compileSdk = 36

    // Uniform product layout: every product in this repository exposes `src/`
    // and `tests/`, whatever its language. That overrides Gradle's default
    // src/main | src/test | src/androidTest convention on purpose — see
    // docs/README.md. Keep these four blocks in sync across the Android modules.
    sourceSets {
        getByName("main") {
            java.setSrcDirs(listOf("src"))
        }
        getByName("test") {
            java.setSrcDirs(listOf("tests/unit"))
        }
        getByName("androidTest") {
            java.setSrcDirs(listOf("tests/instrumented"))
        }
    }

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)

    // EXIF extraction — part of :core's public surface.
    api(libs.androidx.exifinterface)

    // Hilt: the readers and the storage detector are @Singleton @Inject types.
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
}
