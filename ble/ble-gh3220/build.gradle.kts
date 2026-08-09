plugins {
    id("ghealth.android.library")
}

android {
    namespace = "com.ghealth.tools.ble.gh3220"

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
}

dependencies {
    implementation(project(":ble:ble-itlvc"))
    implementation(libs.coroutines.core)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
}
