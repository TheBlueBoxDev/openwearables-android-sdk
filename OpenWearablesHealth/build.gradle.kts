import org.gradle.kotlin.dsl.implementation

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.openwearables.healthsdk"
    compileSdk = 36

    defaultConfig {
        minSdk = 27

        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compileOptions {
            jvmToolchain(17)
        }
    }
}

repositories {
    google()
    mavenCentral()
    mavenLocal {
        url = uri("${project.projectDir}/libs/maven")
    }
}

dependencies {
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)

    implementation(libs.androidx.core.ktx)

    implementation(libs.kotlin.serialization)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.health.connect.client)
//    implementation(libs.health.services.client)

// Samsung Health Data SDK
    implementation(libs.data)

// WorkManager for background sync
    implementation(libs.androidx.work.runtime.ktx)

// Security for EncryptedSharedPreferences
    implementation(libs.androidx.security.crypto)

    testImplementation(libs.junit)
}