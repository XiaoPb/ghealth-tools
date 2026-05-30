package com.goodix.ble.gr.lib.com.transport;

import com.goodix.ble.gr.lib.com.DataProgressListener;
import com.goodix.ble.gr.lib.com.ILogger;

import java.util.List;
import java.util.UUID;

public interface BleConnection {
    String getTargetAddress();
    boolean isConnected();

    void connect() throws Throwable;
    void connect(long timeout) throws Throwable;
    void disconnect() throws Throwable;
    void discoverServices() throws Throwable;
    void setMtu(int newMtu) throws Throwable;

    List<BleService> queryServices(UUID uuid);
    BleCharacteristic queryCharacteristic(BleService service, UUID uuid);
    void enableNotification(BleCharacteristic chr, boolean enabled) throws Throwable;

    void writeChrWithResponse(BleCharacteristic chr, long timeout, byte[] dat, int offsetInDat, int writeSize, DataProgressListener listener) throws Throwable;
    void writeChrWithoutResponse(BleCharacteristic chr, long timeout, byte[] dat, int offsetInDat, int writeSize, DataProgressListener listener) throws Throwable;

    int readNtf(BleCharacteristic chr, long timeout, byte[] outBuf, int offsetInBuf, int readSize) throws Throwable;
    byte[] readNtf(BleCharacteristic chr, long timeout) throws Throwable;

    void setLogger(ILogger logger);
    ILogger getLogger();
}
