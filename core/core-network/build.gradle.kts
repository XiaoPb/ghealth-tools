plugins {
    id("ghealth.android.library")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.ghealth.tools.core.network"
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:core-model"))

    api(libs.retrofit)
    api(libs.retrofit.moshi)
    api(libs.okhttp)
    api(libs.okhttp.logging)
    api(libs.moshi)

    implementation(libs.datastore.preferences)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.timber)
}
