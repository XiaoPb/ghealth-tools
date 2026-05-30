package com.goodix.ble.gr.lib.com.ble;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;

import com.goodix.ble.gr.lib.com.DataProgressListener;
import com.goodix.ble.gr.lib.com.ILogger;
import com.goodix.ble.gr.lib.com.transport.BleCharacteristic;
import com.goodix.ble.gr.lib.com.transport.BleConnection;
import com.goodix.ble.gr.lib.com.transport.BleService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BlockingBleConnection implements BleConnection {
    private final BlockingBle delegate;

    public BlockingBleConnection(BlockingBle ble) {
        this.delegate = ble;
    }

    public BlockingBle getBlockingBle() {
        return delegate;
    }

    @Override
    public String getTargetAddress() {
        return delegate.targetDevice.getAddress();
    }

    @Override
    public boolean isConnected() {
        return delegate.isConnected();
    }

    @Override
    public void connect() throws Throwable {
        delegate.connect();
    }

    @Override
    public void connect(long timeout) throws Throwable {
        delegate.connect(timeout);
    }

    @Override
    public void disconnect() throws Throwable {
        delegate.disconnect();
    }

    @Override
    public void discoverServices() throws Throwable {
        delegate.discoverServices();
    }

    @Override
    public void setMtu(int newMtu) throws Throwable {
        delegate.setMtu(newMtu);
    }

    @Override
    public List<BleService> queryServices(UUID uuid) {
        List<BluetoothGattService> services = delegate.queryServices(uuid);
        List<BleService> result = new ArrayList<>(services.size());
        for (BluetoothGattService svc : services) {
            result.add(new AndroidBleService(svc));
        }
        return result;
    }

    @Override
    public BleCharacteristic queryCharacteristic(BleService service, UUID uuid) {
        if (service instanceof AndroidBleService) {
            BluetoothGattCharacteristic chr = delegate.queryCharacteristic(
                    ((AndroidBleService) service).delegate, uuid);
            return chr != null ? new AndroidBleCharacteristic(chr) : null;
        }
        return null;
    }

    @Override
    public void enableNotification(BleCharacteristic chr, boolean enabled) throws Throwable {
        if (chr instanceof AndroidBleCharacteristic) {
            delegate.enableNotification(((AndroidBleCharacteristic) chr).delegate, enabled);
        }
    }

    @Override
    public void writeChrWithResponse(BleCharacteristic chr, long timeout, byte[] dat, int offsetInDat, int writeSize, DataProgressListener listener) throws Throwable {
        if (chr instanceof AndroidBleCharacteristic) {
            delegate.writeChrWithResponse(((AndroidBleCharacteristic) chr).delegate, timeout, dat, offsetInDat, writeSize, listener);
        }
    }

    @Override
    public void writeChrWithoutResponse(BleCharacteristic chr, long timeout, byte[] dat, int offsetInDat, int writeSize, DataProgressListener listener) throws Throwable {
        if (chr instanceof AndroidBleCharacteristic) {
            delegate.writeChrWithoutResponse(((AndroidBleCharacteristic) chr).delegate, timeout, dat, offsetInDat, writeSize, listener);
        }
    }

    @Override
    public int readNtf(BleCharacteristic chr, long timeout, byte[] outBuf, int offsetInBuf, int readSize) throws Throwable {
        if (chr instanceof AndroidBleCharacteristic) {
            return delegate.readNtf(((AndroidBleCharacteristic) chr).delegate, timeout, outBuf, offsetInBuf, readSize);
        }
        return 0;
    }

    @Override
    public byte[] readNtf(BleCharacteristic chr, long timeout) throws Throwable {
        if (chr instanceof AndroidBleCharacteristic) {
            return delegate.readNtf(((AndroidBleCharacteristic) chr).delegate, timeout);
        }
        return null;
    }

    @Override
    public void setLogger(ILogger logger) {
        delegate.setLogger(logger);
    }

    @Override
    public ILogger getLogger() {
        return delegate.getLogger();
    }

    static class AndroidBleService implements BleService {
        final BluetoothGattService delegate;

        AndroidBleService(BluetoothGattService delegate) {
            this.delegate = delegate;
        }

        @Override
        public UUID getUuid() {
            return delegate.getUuid();
        }
    }

    static class AndroidBleCharacteristic implements BleCharacteristic {
        final BluetoothGattCharacteristic delegate;

        AndroidBleCharacteristic(BluetoothGattCharacteristic delegate) {
            this.delegate = delegate;
        }

        @Override
        public UUID getUuid() {
            return delegate.getUuid();
        }

        @Override
        public int getProperties() {
            return delegate.getProperties();
        }
    }
}
