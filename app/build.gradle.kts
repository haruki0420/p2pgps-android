plugins {
    alias(libs.plugins.android.application)
    // AGP 9 以降は Kotlin サポートが内蔵されているため、kotlin-android プラグインは適用しない。
    alias(libs.plugins.kotlin.compose)
}

// Release builds stamp versionName/versionCode from the git tag in CI
// (`-PappVersionName=... -PappVersionCode=...`). The fallbacks are only for
// local builds; keep them in sync with the latest release.
val appVersionName = providers.gradleProperty("appVersionName").orNull ?: "1.0.6"
val appVersionCode = providers.gradleProperty("appVersionCode").orNull?.toIntOrNull() ?: 10006

// Release signing is configured from the environment in CI (GitHub Secrets).
// Local builds without these variables produce an unsigned release APK.
val releaseKeystorePath = System.getenv("ANDROID_KEYSTORE_PATH")
    ?: providers.gradleProperty("androidKeystorePath").orNull
val releaseKeystorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
    ?: providers.gradleProperty("androidKeystorePassword").orNull
val releaseKeyAlias = System.getenv("ANDROID_KEY_ALIAS")
    ?: providers.gradleProperty("androidKeyAlias").orNull
val releaseKeyPassword = System.getenv("ANDROID_KEY_PASSWORD")
    ?: providers.gradleProperty("androidKeyPassword").orNull

android {
    namespace = "jp.haruki0420.p2pgps"
    compileSdk = 36

    defaultConfig {
        applicationId = "jp.haruki0420.p2pgps"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        if (releaseKeystorePath != null &&
            releaseKeystorePassword != null &&
            releaseKeyAlias != null &&
            releaseKeyPassword != null
        ) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = releaseKeystorePassword
                this.keyAlias = releaseKeyAlias
                this.keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.animation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    // 共有テストベクタ（JSON）を読むために使う。
    testImplementation(libs.org.json)
}
