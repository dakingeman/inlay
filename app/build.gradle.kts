plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.gridime"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.dakingeman.inlay"
        minSdk = 29
        targetSdk = 37
        versionCode = 4
        versionName = "1.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
        // Test builds: "Inlay Preview" installs next to the real Inlay as a separate app,
        // with its own settings. The release build and its app ID are untouched.
        create("preview") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".preview"
            versionNameSuffix = "-preview"
            matchingFallbacks += listOf("debug")
        }
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    // Draws the device pictures (SVG) in the tour, What's new and the Button guide. Apache 2.0
    implementation(libs.androidsvg)
    testImplementation(libs.junit)
    // Test-only: runs Android's real drawing code on a computer, for screenshots of the new screens
    testImplementation("org.robolectric:robolectric:4.17")
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}