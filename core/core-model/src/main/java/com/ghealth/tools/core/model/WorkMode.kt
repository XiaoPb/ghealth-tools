package com.ghealth.tools.core.model

enum class WorkMode(val displayName: String, val key: String) {
    MCU_ONLINE("MCU Online", "mcu_online"),
    PASS_THROUGH("Pass Through", "pass_through"),
    AUTO_PASS("Auto Pass Through", "auto_pass");
}

enum class FunctionMode(val displayName: String, val code: String) {
    ADT("ADT", "ADT"),
    HR("HR", "HR"),
    HRV("HRV", "HRV"),
    SPO2("SpO2", "SPO2"),
    NADT_GREEN("NADT-GREEN", "NADT-GREEN"),
    NADT_IR("NADT-IR", "NADT-IR"),
    TEST1("TEST1", "TEST1"),
    TEST2("TEST2", "TEST2"),
    EVK("EVK", "EVK"),
    ECG("ECG", "ECG"),
    GSR("GSR", "GSR"),
    BIA("BIA", "BIA");
}
