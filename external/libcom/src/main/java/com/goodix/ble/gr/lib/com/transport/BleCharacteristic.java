package com.goodix.ble.gr.lib.com.transport;

import java.util.UUID;

public interface BleCharacteristic {
    UUID getUuid();
    int getProperties();
}
