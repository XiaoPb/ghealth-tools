package com.ghealth.tools.feature.ota.engine

import io.runtime.mcumgr.dfu.mcuboot.model.ImageSet
import io.runtime.mcumgr.dfu.mcuboot.model.TargetImage
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.zip.ZipInputStream

/** SMP 固件包的展示信息。 */
data class SmpPackageInfo(
    val imageCount: Int,
    val totalBytes: Long,
    val manifestFormatVersion: Int,
)

/**
 * Zephyr / nRF Connect SDK 构建产物 ZIP（必须包含 `manifest.json`）。
 *
 * 解析规则与官方 Sample 的 `ZipPackage` 保持一致，仅支持 MCUboot 镜像：
 * - `type` 为 `application` 或 `bin` 的条目会被上传；
 * - `image_index` 缺省为 0（多核设备用于区分核心）；
 * - `slot` 缺省为 secondary，升级后由 bootloader 交换到 primary；
 * - `suit-envelope` / `.suit` 条目明确拒绝。
 */
class SmpZipPackage private constructor(
    val images: ImageSet,
    val info: SmpPackageInfo,
) {

    companion object {
        private const val MANIFEST = "manifest.json"
        private const val TYPE_SUIT_ENVELOPE = "suit-envelope"
        private val SUPPORTED_TYPES = setOf("application", "bin")

        /**
         * @throws IOException ZIP 结构非法、缺少 manifest 或镜像
         * @throws no.nordicsemi.android.mcumgr.exception.McuMgrException 镜像内容无法解析
         */
        fun parse(bytes: ByteArray): SmpZipPackage {
            var manifestText: String? = null
            val entries = mutableMapOf<String, ByteArray>()

            ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.isDirectory) {
                        throw IOException("固件包结构非法：包含目录条目 ${entry.name}")
                    }
                    val name = validateEntryName(entry.name)
                    if (name == MANIFEST) {
                        manifestText = zis.readBytes().toString(Charsets.UTF_8)
                    } else if (name.endsWith(".bin") || name.endsWith(".suit")) {
                        entries[name] = zis.readBytes()
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            val manifest = manifestText
                ?: throw IOException("非 Zephyr/NCS 固件包：缺少 manifest.json")

            val root = try {
                JSONObject(manifest)
            } catch (e: Exception) {
                throw IOException("manifest.json 解析失败: ${e.message}")
            }

            val files = root.optJSONArray("files")
                ?: throw IOException("manifest.json 缺少 files 字段")
            if (files.length() == 0) {
                throw IOException("manifest.json 未声明任何镜像文件")
            }

            val imageSet = ImageSet()
            var totalBytes = 0L
            var count = 0

            for (i in 0 until files.length()) {
                val file = files.optJSONObject(i) ?: continue
                val type = file.optString("type", "")
                val fileName = file.optString("file", "")
                if (type == TYPE_SUIT_ENVELOPE || fileName.endsWith(".suit")) {
                    throw IOException("暂不支持 SUIT 固件包，请使用 MCUboot ZIP")
                }
                if (type !in SUPPORTED_TYPES) continue

                val content = entries[fileName]
                    ?: throw IOException("固件包缺少镜像文件: $fileName")

                val imageIndex = file.optInt("image_index", 0)
                val slot = if (file.has("slot")) {
                    file.optInt("slot", TargetImage.SLOT_SECONDARY)
                } else {
                    TargetImage.SLOT_SECONDARY
                }

                imageSet.add(TargetImage(imageIndex, slot, content))
                totalBytes += content.size
                count++
            }

            if (count == 0) {
                throw IOException("固件包中未找到可升级镜像（application/bin）")
            }

            return SmpZipPackage(
                images = imageSet,
                info = SmpPackageInfo(
                    imageCount = count,
                    totalBytes = totalBytes,
                    manifestFormatVersion = root.optInt("format_version", 0),
                ),
            )
        }

        /** 拒绝绝对路径与路径穿越，避免解压目录逃逸。 */
        private fun validateEntryName(rawName: String): String {
            val name = rawName.replace('\\', '/')
            if (name.startsWith("/") || name.contains("../") || name == ".." || name.contains(":")) {
                throw IOException("固件包包含非法路径: $rawName")
            }
            return name
        }
    }
}
