plugins { id("com.android.application") }

android {
    namespace = "com.socialliviana.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.socialliviana.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 3
        versionName = "1.1.1"
        resourceConfigurations += listOf("es")
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
