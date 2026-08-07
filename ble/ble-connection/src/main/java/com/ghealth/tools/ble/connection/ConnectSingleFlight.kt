package com.ghealth.tools.ble.connection

import java.util.Collections

/**
 * 单地址连接单飞协调器。
 *
 * 背景：autoConnect/connect 可能被上层重复触发（如 ConnectionViewModel 重建时自动重连
 * 与手动连接并发），同一地址的第二个连接若不被拦截，会创建第二个 Peripheral、注册第二个
 * state 观察者并再次订阅 notify，导致每条 GATT 通知被投递两次、帧重复解析/保存。
 *
 * 槽位生命周期：从 [tryAcquire] 成功到 [release]（连接失败或设备断开）为止，
 * 同一地址只允许一个「连接中或已连接」状态。
 */
internal class ConnectSingleFlight {

    private val activeAddresses = Collections.synchronizedSet(mutableSetOf<String>())

    /** 尝试占用 [address] 的槽位（连接中或已连接）；已被占用时返回 false。 */
    fun tryAcquire(address: String): Boolean = activeAddresses.add(address.uppercase())

    /** 连接结束（失败或断开）后释放槽位，允许下次重连。 */
    fun release(address: String) {
        activeAddresses.remove(address.uppercase())
    }

    /** 该地址是否已处于连接中或已连接状态。 */
    fun isActive(address: String): Boolean = activeAddresses.contains(address.uppercase())
}
