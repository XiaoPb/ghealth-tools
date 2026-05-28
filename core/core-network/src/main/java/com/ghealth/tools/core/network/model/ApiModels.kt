package com.ghealth.tools.core.network.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ApiResponse<T>(
    val code: Int,
    val message: String,
    val data: T?
)

@JsonClass(generateAdapter = true)
data class PaginatedListResponse<T>(
    val count: Int,
    val next: String?,
    val previous: String?,
    val results: List<T>
)

@JsonClass(generateAdapter = true)
data class LoginRequest(
    val username: String,
    val password: String
)

@JsonClass(generateAdapter = true)
data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String,
    @Json(name = "password_confirm") val passwordConfirm: String
)

@JsonClass(generateAdapter = true)
data class TokenRefreshRequest(
    val refresh: String
)

@JsonClass(generateAdapter = true)
data class LoginResponse(
    val access: String,
    val refresh: String,
    val user: UserResponse,
    @Json(name = "redirect_url") val redirectUrl: String
)

@JsonClass(generateAdapter = true)
data class UserResponse(
    val id: Int,
    val username: String,
    val email: String,
    @Json(name = "is_staff") val isStaff: Boolean = false,
    @Json(name = "date_joined") val dateJoined: String? = null,
    @Json(name = "project_count") val projectCount: Int = 0
)

@JsonClass(generateAdapter = true)
data class TokenRefreshResponse(
    val access: String
)

@JsonClass(generateAdapter = true)
data class ProjectResponse(
    val id: Int,
    val name: String,
    val owner: Int,
    @Json(name = "owner_name") val ownerName: String,
    @Json(name = "chip_model") val chipModel: String,
    @Json(name = "chip_model_display") val chipModelDisplay: String,
    @Json(name = "chip_model_compatibility") val chipModelCompatibility: String?,
    @Json(name = "hardware_version") val hardwareVersion: String,
    @Json(name = "test_frequency") val testFrequency: String?,
    val description: String?,
    @Json(name = "csv_count") val csvCount: Int,
    @Json(name = "regular_config_count") val regularConfigCount: Int,
    @Json(name = "has_prod_config") val hasProdConfig: Boolean,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "updated_at") val updatedAt: String
)

@JsonClass(generateAdapter = true)
data class ProductionTestConfigResponse(
    val id: Int,
    val project: Int,
    @Json(name = "project_name") val projectName: String,
    @Json(name = "json_config") val jsonConfig: String?,
    @Json(name = "base_noise_config") val baseNoiseConfig: String?,
    @Json(name = "lpctr_config") val lpctrConfig: String?,
    @Json(name = "lplctr_config") val lplctrConfig: String?,
    @Json(name = "ppg_noise_config") val ppgNoiseConfig: String?,
    @Json(name = "hardware_version") val hardwareVersion: String,
    @Json(name = "test_frequency") val testFrequency: String?,
    @Json(name = "file_count") val fileCount: Int,
    @Json(name = "is_complete") val isComplete: Boolean,
    @Json(name = "uploaded_at") val uploadedAt: String?
)

@JsonClass(generateAdapter = true)
data class RegularConfigResponse(
    val id: Int,
    val project: Int,
    @Json(name = "project_name") val projectName: String,
    @Json(name = "config_file") val configFile: String?,
    @Json(name = "config_file_url") val configFileUrl: String?,
    val filename: String,
    val version: String,
    val description: String?,
    @Json(name = "file_size") val fileSize: Long,
    @Json(name = "file_size_display") val fileSizeDisplay: String?,
    @Json(name = "uploaded_by") val uploadedBy: Int,
    @Json(name = "uploaded_by_name") val uploadedByName: String,
    @Json(name = "uploaded_at") val uploadedAt: String
)

@JsonClass(generateAdapter = true)
data class CsvFileResponse(
    val id: Int,
    val project: Int,
    @Json(name = "project_name") val projectName: String,
    @Json(name = "csv_file") val csvFile: String?,
    @Json(name = "csv_file_url") val csvFileUrl: String?,
    val filename: String,
    @Json(name = "row_count") val rowCount: Int,
    @Json(name = "uploaded_by") val uploadedBy: Int,
    @Json(name = "uploaded_by_name") val uploadedByName: String,
    @Json(name = "uploaded_at") val uploadedAt: String
)

@JsonClass(generateAdapter = true)
data class CreateProjectRequest(
    val name: String,
    @Json(name = "chip_model") val chipModel: String,
    @Json(name = "hardware_version") val hardwareVersion: String,
    val description: String = "",
    @Json(name = "test_frequency") val testFrequency: String = ""
)

@JsonClass(generateAdapter = true)
data class UpdateProjectRequest(
    val name: String? = null,
    @Json(name = "chip_model") val chipModel: String? = null,
    @Json(name = "hardware_version") val hardwareVersion: String? = null,
    val description: String? = null,
    @Json(name = "test_frequency") val testFrequency: String? = null
)

@JsonClass(generateAdapter = true)
data class ArchiveActionRequest(
    val action: String
)

@JsonClass(generateAdapter = true)
data class ProjectExportResponse(
    val project: ProjectExportInfo,
    @Json(name = "prod_test_config") val prodTestConfig: ExportProdTestConfig?,
    @Json(name = "regular_configs") val regularConfigs: List<ExportRegularConfig>,
    @Json(name = "csv_files") val csvFiles: List<ExportCsvFile>
)

@JsonClass(generateAdapter = true)
data class ProjectExportInfo(
    val id: Int,
    val name: String,
    @Json(name = "owner_name") val ownerName: String,
    @Json(name = "chip_model_display") val chipModelDisplay: String,
    @Json(name = "hardware_version") val hardwareVersion: String
)

@JsonClass(generateAdapter = true)
data class ExportProdTestConfig(
    val id: Int,
    @Json(name = "is_complete") val isComplete: Boolean,
    @Json(name = "file_count") val fileCount: Int
)

@JsonClass(generateAdapter = true)
data class ExportRegularConfig(
    val id: Int,
    val filename: String,
    val version: String
)

@JsonClass(generateAdapter = true)
data class ExportCsvFile(
    val id: Int,
    val filename: String,
    @Json(name = "row_count") val rowCount: Int
)
