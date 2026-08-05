plugins {
    id("ghealth.android.library.compose")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.ghealth.tools.feature.connection"

    buildFeatures {
        buildConfig = true
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
}

dependencies {
    implementation(project(":core:core-ui"))
    implementation(project(":core:core-model"))
    implementation(project(":core:core-network"))
    implementation(project(":core:core-datastore"))
    implementation(project(":core:core-data"))
    implementation(project(":core:core-storage"))
    implementation(project(":ble:ble-scanner"))
    implementation(project(":ble:ble-connection"))
    implementation(project(":ble:ble-protocol"))
    implementation(project(":feature:feature-factory"))

    implementation(platform(libs.compose.bom))
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.timber)

    testImplementation(libs.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
}
