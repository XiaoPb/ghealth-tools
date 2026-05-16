plugins {
    id("ghealth.android.library")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.ghealth.tools.core.database"
}

dependencies {
    implementation(project(":core:core-model"))

    implementation(libs.room.runtime)
    ksp(libs.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
