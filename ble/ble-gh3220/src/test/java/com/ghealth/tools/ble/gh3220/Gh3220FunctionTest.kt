package com.ghealth.tools.ble.gh3220

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Gh3220FunctionTest {

    /** name 与 C 端 GH3X2X_FUNC_OFFSET_* 宏一一对应（位 0..19）。 */
    private val expectedBits = mapOf(
        "ADT" to 0,
        "HR" to 1,
        "HRV" to 2,
        "HSM" to 3,
        "FPBP" to 4,
        "PWA" to 5,
        "SPO2" to 6,
        "ECG" to 7,
        "PWTT" to 8,
        "SOFT_ADT_GREEN" to 9,
        "BT" to 10,
        "RESP" to 11,
        "AF" to 12,
        "TEST1" to 13,
        "TEST2" to 14,
        "SOFT_ADT_IR" to 15,
        "RS0" to 16,
        "RS1" to 17,
        "RS2" to 18,
        "LEAD_DET" to 19,
    )

    @Test
    fun `枚举共 20 项且 name 与 C 宏一一对应`() {
        assertEquals(20, Gh3220Function.entries.size)
        assertEquals(expectedBits.keys.toList(), Gh3220Function.entries.map { it.name })
    }

    @Test
    fun `bit 与 C GH3X2X_FUNC_OFFSET_* 一致`() {
        expectedBits.forEach { (name, bit) ->
            assertEquals(bit, Gh3220Function.valueOf(name).bit, "GH3X2X_FUNC_OFFSET_$name")
        }
    }

    @Test
    fun `mask 为 1L shl bit`() {
        Gh3220Function.entries.forEach { func ->
            assertEquals(1L shl func.bit, func.mask, func.name)
        }
        assertEquals(0x1L, Gh3220Function.ADT.mask)
        assertEquals(0x2L, Gh3220Function.HR.mask)
        assertEquals(0x40L, Gh3220Function.SPO2.mask)
        assertEquals(0x80L, Gh3220Function.ECG.mask)
        assertEquals(0x100L, Gh3220Function.PWTT.mask)
        assertEquals(0x200L, Gh3220Function.SOFT_ADT_GREEN.mask)
        assertEquals(0x8000L, Gh3220Function.SOFT_ADT_IR.mask)
        assertEquals(0x80000L, Gh3220Function.LEAD_DET.mask)
    }

    @Test
    fun `allMask 覆盖全部 20 位为 0x000FFFFF`() {
        assertEquals(0x000FFFFFL, Gh3220Function.allMask)
        assertEquals(
            Gh3220Function.entries.fold(0L) { acc, func -> acc or func.mask },
            Gh3220Function.allMask,
        )
    }

    @Test
    fun `ofMask 按位还原集合`() {
        assertEquals(
            setOf(Gh3220Function.HR, Gh3220Function.SPO2, Gh3220Function.LEAD_DET),
            Gh3220Function.ofMask(
                Gh3220Function.HR.mask or Gh3220Function.SPO2.mask or Gh3220Function.LEAD_DET.mask,
            ),
        )
        assertEquals(Gh3220Function.entries.toSet(), Gh3220Function.ofMask(Gh3220Function.allMask))
    }

    @Test
    fun `ofMask 掩码为 0 时返回空集`() {
        assertTrue(Gh3220Function.ofMask(0).isEmpty())
    }

    @Test
    fun `ofMask 忽略未知高位`() {
        assertEquals(setOf(Gh3220Function.ADT), Gh3220Function.ofMask(0x1000001L))
        assertTrue(Gh3220Function.ofMask(0x100000L).isEmpty())
    }
}
