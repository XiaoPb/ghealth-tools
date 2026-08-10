import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties
import java.util.TimeZone

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

val buildTimestamp: String = SimpleDateFormat("yyyyMMdd-HHmm").apply {
    timeZone = TimeZone.getTimeZone("Asia/Shanghai")
}.format(Date())

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

android {
    namespace = "com.ghealth.tools"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ghealth.tools"
        minSdk = 24
        targetSdk = 35
        versionCode = 635
        versionName = "0.6.35"
    }

    signingConfigs {
        create("ghealth") {
            storeFile = rootProject.file(keystoreProperties.getProperty("storeFile", "ghealth-release.keystore"))
            storePassword = keystoreProperties.getProperty("storePassword", "")
            keyAlias = keystoreProperties.getProperty("keyAlias", "ghealth")
            keyPassword = keystoreProperties.getProperty("keyPassword", "")
        }
    }

    buildTypes {
        debug {
            versionNameSuffix = "-dev.$buildTimestamp"
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("ghealth")
            versionNameSuffix = "-build.$buildTimestamp"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        checkReleaseBuilds = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        getByName("main") {
            assets.srcDir(rootProject.file("defaults"))
        }
    }

    applicationVariants.all {
        outputs.all {
            val outputFileName = "ghealth-tools-${versionName}.apk"
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName = outputFileName
        }
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

    implementation(project(":feature:feature-ota"))

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

