import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.alialashwal.hotelreservation.core.testing"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        aidl = false
        buildConfig = false
        shaders = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Test helpers are `api` so consuming test source sets get them transitively.
dependencies {
    api(project(":core-model"))
    api(project(":core-domain"))

    api(libs.junit)
    api(libs.kotlinx.coroutines.test)
    api(libs.turbine)

    implementation(libs.androidx.test.runner)
    implementation(libs.hilt.android.testing)
}
