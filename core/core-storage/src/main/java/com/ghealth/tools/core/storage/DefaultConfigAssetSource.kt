package com.ghealth.tools.core.storage

import android.content.res.AssetManager
import java.io.InputStream

/**
 * 抽象 assets 中默认配置目录的访问，便于在 JVM 单测中用本地文件模拟。
 */
interface DefaultConfigAssetSource {
    /** 列出 [path] 下的条目名（文件与子目录）；目录不存在时返回 null。 */
    fun list(path: String): Array<String>?

    /** 打开 [path] 对应的输入流。 */
    fun open(path: String): InputStream
}

/** Android 实现：直接包装 [AssetManager]。 */
class AndroidDefaultConfigAssetSource(
    private val assetManager: AssetManager
) : DefaultConfigAssetSource {
    override fun list(path: String): Array<String>? = assetManager.list(path)

    override fun open(path: String): InputStream = assetManager.open(path)
}
