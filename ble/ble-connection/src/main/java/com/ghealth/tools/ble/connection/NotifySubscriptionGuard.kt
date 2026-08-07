package com.ghealth.tools.ble.connection

import java.util.Collections

/**
 * 每地址 notify 订阅登记表：保证同一连接内 notify 特征只被订阅一次。
 * 重复订阅会让同一条 GATT 通知被投递多次，导致下游帧重复解析/保存。
 */
internal class NotifySubscriptionGuard {

    private val subscribedAddresses = Collections.synchronizedSet(mutableSetOf<String>())

    /** 登记 [address] 的订阅；若已登记返回 false（重复订阅被拦截）。 */
    fun tryRegister(address: String): Boolean = subscribedAddresses.add(address.uppercase())

    /** 设备断开后注销，允许下次连接重新订阅。 */
    fun unregister(address: String) {
        subscribedAddresses.remove(address.uppercase())
    }
}
