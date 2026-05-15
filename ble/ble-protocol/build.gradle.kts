plugins {
    id("ghealth.android.library")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.ghealth.tools.ble.protocol"
}

dependencies {
    implementation(project(":core:core-model"))
    implementation(libs.coroutines.core)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.timber)

    testImplementation(libs.junit5)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
}
