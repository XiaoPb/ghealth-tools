plugins {
    id("ghealth.android.library")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.ghealth.tools.core.datastore"

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
}

dependencies {
    implementation(project(":core:core-model"))
    implementation(libs.datastore.preferences)
    implementation(libs.security.crypto)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.coroutines.core)

    testImplementation(libs.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
}
