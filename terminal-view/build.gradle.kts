plugins {
    id("com.android.library")
}

android {
    namespace = "com.termux.view"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("proguard-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    buildFeatures {
        // no viewBinding needed
    }
}

dependencies {
    implementation(project(":terminal-emulator"))
    implementation("androidx.annotation:annotation:1.8.2")
}
