package com.ghealth.tools.ble.connection

/**
 * 单地址连接单飞协调器（按连接归属释放）。
 *
 * 背景：autoConnect/connect 可能被上层重复触发（如 ConnectionViewModel 重建时自动重连
 * 与手动连接并发），同一地址的第二个连接若不被拦截，会创建第二个 Peripheral、注册第二个
 * state 观察者并再次订阅 notify，导致每条 GATT 通知被投递两次、帧重复解析/保存。
 *
 * 槽位生命周期：从 [tryAcquire] 成功到该 owner 的 [release]（连接失败或设备断开）为止，
 * 同一地址只允许一个「连接中或已连接」状态。release 按 owner 归属判断，过期断连回调
 * （owner 已被新连接替换）不会误释放新连接的槽位。
 */
internal class ConnectSingleFlight {

    private val lock = Any()
    private val owners = mutableMapOf<String, Any>()

    /** 尝试以 [owner] 占用 [address] 的槽位（连接中或已连接）；已被占用时返回 false。 */
    fun tryAcquire(address: String, owner: Any): Boolean = synchronized(lock) {
        owners.putIfAbsent(address.uppercase(), owner) == null
    }

    /** 仅当 [address] 当前归属 [owner] 时释放槽位；过期回调自动忽略，允许下次重连。 */
    fun release(address: String, owner: Any) {
        synchronized(lock) {
            if (owners[address.uppercase()] === owner) {
                owners.remove(address.uppercase())
            }
        }
    }

    /** 该地址是否已处于连接中或已连接状态。 */
    fun isActive(address: String): Boolean = synchronized(lock) {
        owners.containsKey(address.uppercase())
    }
}
