package com.goodix.ble.gr.lib.com.transport;

public interface DfuReconnectHandler {
    String getCurrentDeviceAddress();
    BleConnection scanAndConnect(String targetMac, long timeoutMs) throws Throwable;
}
