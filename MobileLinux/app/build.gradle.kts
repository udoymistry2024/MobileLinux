plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-parcelize")
}

android {
    namespace = "com.mobilelinux"
    compileSdk = 35  // Android 15

    defaultConfig {
        applicationId = "com.mobilelinux.app"
        minSdk = 26          // Android 8.0 minimum
        targetSdk = 35       // Target Android 15
        versionCode = 65
        versionName = "1.7.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ABI filter — ARM64 is primary (all modern Android phones)
        // armeabi-v7a for older 32-bit devices
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("${rootDir}/mobilelinux.jks")
            storePassword = "mobilelinux123"
            keyAlias = "mobilelinux"
            keyPassword = "mobilelinux123"
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("release")
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
        viewBinding = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            // proot binary packaged as .so — preserve exact bytes
            useLegacyPackaging = true
            // Don't compress .so files — proot must be directly executable
            keepDebugSymbols += "**/*.so"
        }
    }

    // ONE APK for all ABIs (proot binary included for arm64 + armv7)
    splits {
        abi {
            isEnable = false  // Disable ABI splits — single universal APK
        }
    }

    // Don't compress specific asset types
    androidResources {
        noCompress += listOf("tar", "xz", "gz", "sh")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.webkit)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.commons.compress)
    implementation(libs.tukaani.xz)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
