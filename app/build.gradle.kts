plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.nameplateexcel"
    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }

    defaultConfig {
        applicationId = "com.example.nameplateexcel"
        minSdk = 31
        targetSdk = 37
        versionCode = 9
        versionName = "2.3.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    androidResources {
        noCompress += "litertlm"
    }
}

dependencies {
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.11.0")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0") {
        // Password-protected PDFs are intentionally unsupported. Excluding the
        // optional crypto stack keeps the offline APK smaller and avoids loading
        // obsolete Bouncy Castle dependencies for plain-text extraction.
        exclude(group = "org.bouncycastle")
    }
    testImplementation("junit:junit:4.13.2")
}
