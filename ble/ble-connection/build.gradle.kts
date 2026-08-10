plugins {
    id("ghealth.android.library")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.ghealth.tools.ble.connection"

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
}

dependencies {
    implementation(project(":core:core-model"))
    implementation(project(":core:core-datastore"))
    implementation(project(":core:core-storage"))
    implementation(project(":ble:ble-scanner"))
    implementation(project(":ble:ble-protocol"))
    implementation(project(":ble:ble-itlvc"))
    implementation(project(":ble:ble-gh3220"))
    implementation(libs.kable.core.android)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.coroutines.core)
    implementation(libs.timber)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.coroutines.test)
}
