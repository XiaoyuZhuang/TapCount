plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.xiaoyuzhuang.tapcount"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.xiaoyuzhuang.tapcount"
        minSdk = 26
        targetSdk = 34
        versionCode = 9
        versionName = "0.3.5"
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    val keyPath = System.getenv("TAPCOUNT_KEYSTORE_PATH")
    val keyPassword = System.getenv("TAPCOUNT_KEYSTORE_PASSWORD")
    val keyAlias = System.getenv("TAPCOUNT_KEY_ALIAS")
    val keyPass = System.getenv("TAPCOUNT_KEY_PASSWORD")
    if (!keyPath.isNullOrBlank() && !keyPassword.isNullOrBlank() &&
        !keyAlias.isNullOrBlank() && !keyPass.isNullOrBlank()) {
        signingConfigs {
            create("production") {
                storeFile = file(keyPath)
                storePassword = keyPassword
                storeType = "PKCS12"
                this.keyAlias = keyAlias
                this.keyPassword = keyPass
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (signingConfigs.findByName("production") != null)
                signingConfig = signingConfigs.getByName("production")
        }
    }
}
