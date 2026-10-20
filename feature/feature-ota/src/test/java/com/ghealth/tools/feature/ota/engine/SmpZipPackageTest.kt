package com.ghealth.tools.feature.ota.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SmpZipPackageTest {

    @Test
    fun `valid single image package parsed`() {
        val image = mcubootImage()
        val packageBytes = zipOf(
            "manifest.json" to manifest(
                """{"file":"app.bin","type":"application","image_index":0}"""
            ).toByteArray(),
            "app.bin" to image,
        )

        val packageInfo = SmpZipPackage.parse(packageBytes)

        assertEquals(1, packageInfo.info.imageCount)
        assertEquals(image.size.toLong(), packageInfo.info.totalBytes)
        assertEquals(1, packageInfo.info.manifestFormatVersion)
    }

    @Test
    fun `package without manifest rejected`() {
        val packageBytes = zipOf("app.bin" to ByteArray(16))

        val error = assertThrows(IOException::class.java) { SmpZipPackage.parse(packageBytes) }
        assertEquals("非 Zephyr/NCS 固件包：缺少 manifest.json", error.message)
    }

    @Test
    fun `path traversal entry rejected`() {
        val packageBytes = zipOf(
            "manifest.json" to manifest(
                """{"file":"app.bin","type":"application","image_index":0}"""
            ).toByteArray(),
            "../app.bin" to ByteArray(16),
        )

        assertThrows(IOException::class.java) { SmpZipPackage.parse(packageBytes) }
    }

    @Test
    fun `suit envelope rejected`() {
        val packageBytes = zipOf(
            "manifest.json" to manifest(
                """{"file":"app.suit","type":"suit-envelope","image_index":0}"""
            ).toByteArray(),
            "app.suit" to ByteArray(16),
        )

        val error = assertThrows(IOException::class.java) { SmpZipPackage.parse(packageBytes) }
        assertEquals("暂不支持 SUIT 固件包，请使用 MCUboot ZIP", error.message)
    }

    @Test
    fun `declared image missing from archive rejected`() {
        val packageBytes = zipOf(
            "manifest.json" to manifest(
                """{"file":"missing.bin","type":"application","image_index":0}"""
            ).toByteArray(),
        )

        val error = assertThrows(IOException::class.java) { SmpZipPackage.parse(packageBytes) }
        assertEquals("固件包缺少镜像文件: missing.bin", error.message)
    }

    private fun manifest(file: String) =
        """{"format_version":1,"files":[$file]}"""

    /**
     * 构造最小可解析的 MCUboot 镜像：32 字节头 + 32 字节数据区 + TLV trailer。
     * `TargetImage` 会解析镜像头与 TLV 中的 hash，因此测试不能直接用随机字节。
     */
    private fun mcubootImage(): ByteArray {
        val headerSize = 32
        val dataSize = 32
        val tlvOffset = headerSize + dataSize
        val hash = ByteArray(32)

        val buffer = ByteBuffer.allocate(tlvOffset + 4 + 4 + hash.size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(0x96F3B83D.toInt()) // magic
        buffer.putInt(0) // load_addr
        buffer.putShort(headerSize.toShort()) // hdr_size
        buffer.putShort(0) // protect_tlv_size
        buffer.putInt(dataSize) // img_size
        buffer.putInt(0) // flags
        buffer.put(0).put(0).putShort(0).putInt(0) // version
        buffer.position(tlvOffset)
        buffer.putShort(0x6907) // TLV info magic
        buffer.putShort((4 + hash.size).toShort()) // TLV total
        buffer.put(0x10) // hash TLV
        buffer.putShort(hash.size.toShort())
        buffer.put(hash)
        return buffer.array()
    }

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
