package com.ghealth.tools.navigation

object Routes {
    const val LOGIN = "login"
    const val CHIP_SELECTION = "chip_selection"
    const val REGISTER = "register"
    const val PROJECT_SELECTION = "project_selection"
    const val PROJECT_CREATE = "project_create"
    const val CONFIG_UPLOAD = "config_upload/{projectId}/{projectName}"
    const val MAIN = "main"

    object Main {
        const val CONNECTION = "connection"
        const val DEMO = "demo"
        const val SETTINGS = "settings"
        const val DEVICE_INFO = "device_info"
        const val FACTORY = "factory"
    }

    fun configUpload(projectId: Int, projectName: String): String =
        "config_upload/$projectId/$projectName"
}