import org.gradle.kotlin.dsl.implementation

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("maven-publish")
}

android {
    namespace = "com.openwearables.health.sdk"
    compileSdk = 36

    defaultConfig {
        minSdk = 29

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

    publishing {
        singleVariant("release") {
            withSourcesJar()
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

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.openwearables.health"
                artifactId = "sdk"
                version = "0.9.0"

                pom {
                    name.set("Open Wearables Health SDK")
                    description.set("Android SDK for reading and syncing health data from Samsung Health and Health Connect")
                    url.set("https://github.com/the-momentum/open_wearables_android_sdk")
                }
            }
        }

        repositories {
            maven {
                name = "mavenLocal"
                url = uri("${System.getProperty("user.home")}/.m2/repository")
            }
        }
    }
}

tasks.register<Copy>("installSamsungSdkToMavenLocal") {
    from("${project.projectDir}/libs/maven/com/samsung/android/health/data")
    into("${System.getProperty("user.home")}/.m2/repository/com/samsung/android/health/data")
}

tasks.matching { it.name.contains("PublicationToMavenLocal") }.configureEach {
    dependsOn("installSamsungSdkToMavenLocal")
}

// Publish SDK + Samsung dependency to the Flutter plugin's bundled repo
val flutterRepoDir = providers.gradleProperty("flutterPluginRepo").orNull

if (flutterRepoDir != null) {
    afterEvaluate {
        publishing {
            repositories {
                maven {
                    name = "flutterPlugin"
                    url = uri(flutterRepoDir)
                }
            }
        }

        tasks.register<Copy>("installSamsungSdkToFlutterPlugin") {
            from("${project.projectDir}/libs/maven/com/samsung/android/health/data")
            into("$flutterRepoDir/com/samsung/android/health/data")
        }

        tasks.matching { it.name.contains("PublicationToFlutterPlugin") }.configureEach {
            dependsOn("installSamsungSdkToFlutterPlugin")
        }
    }
}
