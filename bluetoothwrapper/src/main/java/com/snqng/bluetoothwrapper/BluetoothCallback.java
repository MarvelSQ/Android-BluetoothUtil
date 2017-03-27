package com.snqng.bluetoothwrapper;

import android.bluetooth.BluetoothDevice;

/**
 * Created by sunqiang on 2017/3/23.
 */
public interface BluetoothCallback {

    void onBluetooth(int state);

    void onStartDiscovery();

    void onFinishDiscovery();

    void onDeviceFound(String name, BluetoothDevice device, int rssi, byte[] scanRecord);

    class Null implements BluetoothCallback{

        @Override
        public void onBluetooth(int state) {

        }

        @Override
        public void onStartDiscovery() {

        }

        @Override
        public void onFinishDiscovery() {

        }

        @Override
        public void onDeviceFound(String name, BluetoothDevice device, int rssi, byte[] scanRecord) {

        }
    }

    /*abstract class Ble implements BluetoothCallback{

        @Override
        public void onStartDiscovery() { *//* *//*}

        @Override
        public void onFinishDiscovery() {
            *//* *//*
        }

        @Override
        public abstract void onDeviceFound(String name, BluetoothDevice device, int rssi, byte[] scanRecord);
    }*/
}
