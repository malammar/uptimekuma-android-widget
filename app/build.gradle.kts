plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.sifrlabs.uptimekuma"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sifrlabs.uptimekuma"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }

    val localProps = java.util.Properties().apply {
        val f = rootProject.file("local.properties")
        if (f.exists()) load(f.inputStream())
    }

    signingConfigs {
        create("upload") {
            storeFile = file("../upload-key.jks")
            storePassword = localProps.getProperty("upload.storePassword")
            keyAlias = localProps.getProperty("upload.keyAlias")
            keyPassword = localProps.getProperty("upload.keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("upload")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.1.0")
}
