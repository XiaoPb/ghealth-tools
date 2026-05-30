plugins {
    id("ghealth.android.library")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.ghealth.tools.ble.connection"
}

dependencies {
    implementation(project(":core:core-model"))
    implementation(project(":core:core-datastore"))
    implementation(project(":core:core-storage"))
    implementation(project(":ble:ble-scanner"))
    implementation(project(":ble:ble-protocol"))
    implementation(libs.kable.core.android)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.coroutines.core)
    implementation(libs.timber)
}
