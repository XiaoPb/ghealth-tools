package com.ghealth.tools.core.storage

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class CsvWriterTest {

    @TempDir
    lateinit var baseDir: File

    @Test
    fun `info 行中的换行符写为字面反斜杠 n`() = runTest {
        val file = File(baseDir, "result.csv")
        val rule = CsvRule(chip = "test", columns = listOf("first", "second"))
        val infoJson = "{\"info\":\"first\r\nsecond\nthird\rfour\"}"
        val writer = CsvWriter(file, rule, infoJson)

        writer.open()
        writer.close()

        assertEquals(
            listOf(
                """{"info":"first\nsecond\nthird\nfour"}""",
                "first,second"
            ),
            file.readLines()
        )
    }
}
