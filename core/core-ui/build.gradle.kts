plugins {
    id("ghealth.android.library.compose")
}

android {
    namespace = "com.ghealth.tools.core.ui"
}

dependencies {
    implementation(project(":core:core-model"))
    implementation(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.ui.graphics)
    api(libs.compose.ui.tooling.preview)
    api(libs.compose.material3)
    api(libs.compose.material.icons)
    api(libs.compose.material3.wnd)
    debugImplementation(libs.compose.ui.tooling)
}
