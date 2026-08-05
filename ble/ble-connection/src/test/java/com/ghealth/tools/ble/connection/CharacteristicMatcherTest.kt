@file:OptIn(ExperimentalUuidApi::class)

package com.ghealth.tools.ble.connection

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CharacteristicMatcherTest {

    private val writeUuid = Uuid.parse("00000003-0000-1000-8000-00805f9b34fb")
    private val notifyUuid = Uuid.parse("00000004-0000-1000-8000-00805f9b34fb")
    private val configuredServiceUuid = Uuid.parse("0000190e-0000-1000-8000-00805f9b34fb")
    private val otherServiceUuid = Uuid.parse("0000180f-0000-1000-8000-00805f9b34fb")

    private fun ref(service: Uuid, char: Uuid) = DiscoveredCharacteristicRef(service, char)

    @Test
    fun `write and notify in the same service match that service uuid`() {
        val refs = listOf(
            ref(configuredServiceUuid, writeUuid),
            ref(configuredServiceUuid, notifyUuid),
        )
        val matched = CharacteristicMatcher.match(refs, writeUuid, notifyUuid)
            as CharacteristicMatcher.Result.Matched
        assertEquals(configuredServiceUuid, matched.writeServiceUuid)
        assertEquals(configuredServiceUuid, matched.notifyServiceUuid)
    }

    @Test
    fun `write and notify in different services match each respective service uuid`() {
        val refs = listOf(
            ref(otherServiceUuid, writeUuid),
            ref(configuredServiceUuid, notifyUuid),
        )
        val matched = CharacteristicMatcher.match(refs, writeUuid, notifyUuid)
            as CharacteristicMatcher.Result.Matched
        assertEquals(otherServiceUuid, matched.writeServiceUuid)
        assertEquals(configuredServiceUuid, matched.notifyServiceUuid)
    }

    @Test
    fun `ignores configured service uuid and matches by characteristic uuid across mismatched service`() {
        // 设备实际服务 UUID 与配置不一致，但特征 UUID 命中——仍应匹配成功
        val refs = listOf(
            ref(otherServiceUuid, writeUuid),
            ref(otherServiceUuid, notifyUuid),
        )
        val matched = CharacteristicMatcher.match(refs, writeUuid, notifyUuid)
            as CharacteristicMatcher.Result.Matched
        assertEquals(otherServiceUuid, matched.writeServiceUuid)
        assertEquals(otherServiceUuid, matched.notifyServiceUuid)
    }

    @Test
    fun `write characteristic not present in any service returns WriteNotFound`() {
        val refs = listOf(ref(configuredServiceUuid, notifyUuid))
        val result = CharacteristicMatcher.match(refs, writeUuid, notifyUuid)
        assertEquals(CharacteristicMatcher.Result.WriteNotFound, result)
    }

    @Test
    fun `notify characteristic not present in any service returns NotifyNotFound`() {
        val refs = listOf(ref(configuredServiceUuid, writeUuid))
        val result = CharacteristicMatcher.match(refs, writeUuid, notifyUuid)
        assertEquals(CharacteristicMatcher.Result.NotifyNotFound, result)
    }

    @Test
    fun `empty service list returns WriteNotFound`() {
        val result = CharacteristicMatcher.match(emptyList(), writeUuid, notifyUuid)
        assertEquals(CharacteristicMatcher.Result.WriteNotFound, result)
    }

    @Test
    fun `duplicate characteristic uuid across services returns first occurrence service uuid`() {
        val firstService = Uuid.parse("0000180a-0000-1000-8000-00805f9b34fb")
        val refs = listOf(
            ref(firstService, writeUuid),
            ref(otherServiceUuid, writeUuid), // 重复
            ref(configuredServiceUuid, notifyUuid),
        )
        val matched = CharacteristicMatcher.match(refs, writeUuid, notifyUuid)
            as CharacteristicMatcher.Result.Matched
        assertEquals(firstService, matched.writeServiceUuid)
    }

    @Test
    fun `characteristics spread across three services with unrelated chars all match correctly`() {
        val thirdService = Uuid.parse("0000181a-0000-1000-8000-00805f9b34fb")
        val unrelated = Uuid.parse("00002a00-0000-1000-8000-00805f9b34fb")
        val refs = listOf(
            ref(configuredServiceUuid, unrelated),
            ref(otherServiceUuid, writeUuid),
            ref(thirdService, notifyUuid),
        )
        val matched = CharacteristicMatcher.match(refs, writeUuid, notifyUuid)
            as CharacteristicMatcher.Result.Matched
        assertEquals(otherServiceUuid, matched.writeServiceUuid)
        assertEquals(thirdService, matched.notifyServiceUuid)
    }

    @Test
    fun `write checked before notify when both missing returns WriteNotFound`() {
        val unrelated = Uuid.parse("00002a00-0000-1000-8000-00805f9b34fb")
        val refs = listOf(ref(configuredServiceUuid, unrelated))
        val result = CharacteristicMatcher.match(refs, writeUuid, notifyUuid)
        assertEquals(CharacteristicMatcher.Result.WriteNotFound, result)
    }

    @Test
    fun `matched result is a data class with stable equality`() {
        val refs = listOf(
            ref(otherServiceUuid, writeUuid),
            ref(configuredServiceUuid, notifyUuid),
        )
        val a = CharacteristicMatcher.match(refs, writeUuid, notifyUuid)
        val b = CharacteristicMatcher.match(refs, writeUuid, notifyUuid)
        assertTrue(a is CharacteristicMatcher.Result.Matched)
        assertEquals(a, b)
    }
}
