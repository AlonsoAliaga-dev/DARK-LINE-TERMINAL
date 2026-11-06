plugins {
    id("com.android.library")
}

android {
    namespace = "com.termux.emulator"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("proguard-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    // Prebuilt libtermux.so is provided under jniLibs of the app module.
    // If you want to build from source instead, uncomment externalNativeBuild below
    // and ensure NDK is installed.
    /*
    externalNativeBuild {
        ndkBuild {
            path = file("src/main/jni/Android.mk")
        }
    }
    */
}

dependencies {
    implementation("androidx.annotation:annotation:1.8.2")
}
