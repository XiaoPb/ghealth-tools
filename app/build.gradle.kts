import java.text.SimpleDateFormat
import java.util.Date

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

val buildTimestamp: String = SimpleDateFormat("yyyyMMdd-HHmm").format(Date())

android {
    namespace = "com.ghealth.tools"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ghealth.tools"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0-dev.$buildTimestamp"
    }

    signingConfigs {
        create("ghealth") {
            storeFile = rootProject.file("ghealth-release.keystore")
            storePassword = "ghealth2026"
            keyAlias = "ghealth"
            keyPassword = "ghealth2026"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("ghealth")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        checkReleaseBuilds = false
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:core-ui"))
    implementation(project(":core:core-common"))
    implementation(project(":core:core-model"))
    implementation(project(":core:core-data"))
    implementation(project(":core:core-database"))
    implementation(project(":core:core-datastore"))
    implementation(project(":core:core-storage"))

    implementation(project(":feature:feature-login"))
    implementation(project(":feature:feature-connection"))
    implementation(project(":feature:feature-demo"))
    implementation(project(":feature:feature-factory"))
    implementation(project(":feature:feature-settings"))

    implementation(project(":ble:ble-scanner"))
    implementation(project(":ble:ble-connection"))
    implementation(project(":ble:ble-protocol"))

    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.wnd)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.runtime.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.timber)
}
