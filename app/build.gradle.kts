plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.sourabh.jarvistube"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.sourabh.jarvistube"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "0.1"
        manifestPlaceholders["appLabel"] = "YouTube"
    }

    buildTypes {
        release {
            // Personal-use build: signed with the debug key so it installs directly.
            signingConfig = signingConfigs.getByName("debug")
            optimization {
                enable = false
            }
        }
        // Release build that installs next to the real app (own package, data and study
        // provider), for testing on the phone without touching the real app's locks or data.
        create("preview") {
            initWith(getByName("release"))
            applicationIdSuffix = ".preview"
            manifestPlaceholders["appLabel"] = "YouTube"
            matchingFallbacks += "release"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/DEPENDENCIES",
                "META-INF/INDEX.LIST"
            )
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar)
    testImplementation("junit:junit:4.13.2")

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.datasource.okhttp)

    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.androidx.work.runtime)
    implementation(libs.coil.network.okhttp)
    implementation(libs.newpipe.extractor)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
