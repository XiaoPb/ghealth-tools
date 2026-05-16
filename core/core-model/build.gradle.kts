plugins {
    id("org.jetbrains.kotlin.android")
    id("com.android.library")
}

android {
    namespace = "com.ghealth.tools.core.model"
    compileSdk = 36
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
