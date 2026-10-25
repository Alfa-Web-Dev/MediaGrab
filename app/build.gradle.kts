plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.mediagrab"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.mediagrab"
        minSdk = 29            // MediaStore.Downloads needs no storage permission on 29+
        targetSdk = 35
        versionCode = 3
        versionName = "0.3.0"
    }

    // One small APK per CPU type + one universal APK that works everywhere.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = true
        }
    }

    // Signing: values come from environment variables (set by GitHub Actions from your secrets).
    // If they are missing (e.g. a normal local build), the release APK is simply left unsigned.
    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_PATH")
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (System.getenv("KEYSTORE_PATH") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            // Turn this on LATER and test every feature: the yt-dlp library uses reflection/JSON.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }

    // Lint warnings must never stop the APK from being built.
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    // Required: the bundled yt-dlp/python/ffmpeg binaries must be extracted to disk.
    packaging {
        jniLibs { useLegacyPackaging = true }
    }
}

dependencies {
    // Engine: yt-dlp + Python + FFmpeg wrapper.
    // VERIFY the latest version/coordinates on the project's GitHub page before building.
    val ytdlVersion = "0.17.2"
    implementation("io.github.junkfood02.youtubedl-android:library:$ytdlVersion")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:$ytdlVersion")

    // Compose
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Thumbnails
    implementation("io.coil-kt:coil-compose:2.7.0")
}
