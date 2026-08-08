package com.ghealth.tools.core.network

/**
 * 记录登录时一次性验证的端点可用性结果。
 * [usePrimary] 返回 true=primary 可用，false=primary 不可用，null=尚未验证。
 */
interface EndpointPreference {
    fun usePrimary(): Boolean?

    suspend fun setUsePrimary(usePrimary: Boolean)
}
