package com.goodix.ble.gr.lib.com.transport;

public final class BleProperty {
    public static final int BROADCAST = 0x0001;
    public static final int READ = 0x0002;
    public static final int WRITE_NO_RESPONSE = 0x0004;
    public static final int WRITE = 0x0008;
    public static final int NOTIFY = 0x0010;
    public static final int INDICATE = 0x0020;
    public static final int SIGNED_WRITE = 0x0040;
    public static final int EXTENDED_PROPS = 0x0080;

    private BleProperty() {}
}
