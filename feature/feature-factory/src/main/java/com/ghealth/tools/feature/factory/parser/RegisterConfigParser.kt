package com.ghealth.tools.feature.factory.parser

import com.ghealth.tools.feature.factory.model.RegEntry
import com.ghealth.tools.feature.factory.model.RegisterConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RegisterConfigParser @Inject constructor() {

    private val hexPairRegex = Regex("""\{0x([0-9a-fA-F]+)\s*,\s*0x([0-9a-fA-F]+)\}""")
    private val iniHexPairRegex = Regex("""\{0x([0-9a-fA-F]+)\s*,\s*0x([0-9a-fA-F]+)\}""")

    fun parseGh3036(content: String): RegisterConfig {
        val registers = mutableListOf<RegEntry>()

        var inRegisterList = false
        var headerSkipped = false

        for (line in content.lines()) {
            val trimmed = line.trim()

            if (trimmed.startsWith("[")) {
                inRegisterList = trimmed.startsWith("[Register_List]")
                headerSkipped = false
                continue
            }

            if (!inRegisterList) continue
            if (trimmed.isEmpty()) continue

            // Skip the header line "addr, value, default"
            if (!headerSkipped && trimmed.startsWith("addr")) {
                headerSkipped = true
                continue
            }
            headerSkipped = true

            val match = hexPairRegex.find(trimmed)
            if (match != null) {
                registers.add(
                    RegEntry(
                        addr = match.groupValues[1].toInt(16),
                        value = match.groupValues[2].toInt(16)
                    )
                )
            }
        }

        return RegisterConfig(registers)
    }

    fun parseGh3220(content: String): RegisterConfig {
        val registers = mutableListOf<RegEntry>()

        var inRelevantSection = false

        for (line in content.lines()) {
            val trimmed = line.trim()

            if (trimmed.startsWith("[")) {
                inRelevantSection = trimmed.startsWith("[drvregister-table]") ||
                        trimmed.startsWith("[algoregister-table]")
                continue
            }

            if (!inRelevantSection) continue
            if (trimmed.isEmpty()) continue
            if (trimmed.startsWith("static") || trimmed.startsWith("const")) continue

            iniHexPairRegex.findAll(trimmed).forEach { match ->
                registers.add(
                    RegEntry(
                        addr = match.groupValues[1].toInt(16),
                        value = match.groupValues[2].toInt(16)
                    )
                )
            }
        }

        return RegisterConfig(registers)
    }

    fun parseByChip(content: String, chip: String, fileName: String): RegisterConfig =
        when (chip.lowercase()) {
            "gh3036", "gh3300" -> parseGh3036(content)
            "gh3220" -> parseGh3220(content)
            else -> {
                // Auto-detect by extension
                if (fileName.endsWith(".ini")) parseGh3220(content)
                else parseGh3036(content)
            }
        }
}
