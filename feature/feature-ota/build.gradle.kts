plugins {
    id("ghealth.android.library.compose")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.ghealth.tools.feature.ota"

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
}

dependencies {
    implementation(project(":core:core-ui"))
    implementation(project(":core:core-model"))
    implementation(project(":core:core-datastore"))
    implementation(project(":core:core-network"))
    implementation(project(":ble:ble-connection"))
    implementation(project(":external:libcom"))
    implementation(project(":external:libdfu2"))

    implementation(platform(libs.compose.bom))
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.timber)

    testImplementation(libs.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
}