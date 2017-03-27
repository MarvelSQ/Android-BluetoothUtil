package com.snqng.bluetoothwrapper;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.support.annotation.RequiresApi;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.UUID;

/**
 * Created by sunqiang on 2017/3/20.
 */

public class BluetoothWrapper {

    private String TAG = BluetoothWrapper.class.getSimpleName();

    public final static int ENABLE_STATE_ON = 1;
    public final static int ENABLE_STATE_OFF = 0;

    private BluetoothManager mManager;

    private BluetoothAdapter mAdapter;

    private Context mContext;

    private BluetoothCallback mCallback;

    private UUID mSocketUuid;

    @RequiresApi(android.os.Build.VERSION_CODES.JELLY_BEAN_MR2)
    private BluetoothAdapter.LeScanCallback mScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {

            mCallback.onDeviceFound(device.getName(), device, rssi, scanRecord);
        }
    };

    private BluetoothDevice mDevice;

    private BluetoothSocket mClientSocket;

    public static final int STATE_NONE = 0;

    public static final int STATE_CONNECTING = 1;

    public static final int STATE_CONNECTED = 2;


    private int mState;

    private BluetoothServerSocket mServerSocket;

    private InputStream mInputStream;

    private OutputStream mOutputStream;
    private ConnectedThread mConnectedThread;
    private ConnectThread mConnectThread;

    public BluetoothWrapper(Context context, BluetoothCallback uiCallback) {
        mContext = context;
        mCallback = uiCallback;
    }

    public boolean checkBleHardware() {
        BluetoothManager manager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
        if (manager == null) return false;
        BluetoothAdapter adapter = manager.getAdapter();
        if (adapter == null) return false;
        boolean hasBt = mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
        return hasBt;
    }

    public boolean isBluetoothOn() {
        BluetoothManager manager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
        if (manager == null) return false;

        BluetoothAdapter adapter = manager.getAdapter();
        if (adapter == null) return false;

        return adapter.isEnabled();
    }

    /**
     * initialize BLE and get BT Manager & Adapter
     * run this method after use {@link #checkBleHardware()}
     *
     * @return true execute successfully, if false maybe the hardware doesn't support!
     */
    public boolean initialize() {
        if (mManager == null) {
            mManager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
            if (mManager == null) {
                return false;
            }
        }

        if (mAdapter == null) mAdapter = mManager.getAdapter();
        if (mAdapter == null) {
            return false;
        }
        mContext.registerReceiver(mReceiver, makeFilter());
        return true;
    }

    public void startScan() {
        mAdapter.startDiscovery();
    }

    public void startLeScan() {
        mAdapter.startLeScan(mScanCallback);
    }

    /**
     * use UUID[] to scan device
     * error: use this method can not return any info
     *
     * @param uuids for scanning the match device
     */
    public void startLeScan(UUID[] uuids) {
        mAdapter.startLeScan(uuids, mScanCallback);
    }

    public void cancelScan() {
        mAdapter.cancelDiscovery();
    }

    public void cancelLeScan() {
        mAdapter.stopLeScan(mScanCallback);
    }

    public void connect(BluetoothDevice device) {
        connect(device, UUID.fromString(BluetoothUtil.NEW));
    }

    public void connect(BluetoothDevice device, UUID socketUuid) {
        mSocketUuid = socketUuid;
        mDevice = device;
        mConnectThread = new ConnectThread(device, socketUuid);
        mConnectThread.start();
    }

    public void clear() {
        mContext.unregisterReceiver(mReceiver);
    }

    private IntentFilter makeFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        //filter.addAction(BluetoothDevice.ACTION_FOUND);
        return filter;
    }

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case BluetoothAdapter.ACTION_STATE_CHANGED:
                    int blueState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0);
                    switch (blueState) {
                        case BluetoothAdapter.STATE_TURNING_ON:
                            Log.e(TAG, "TURNING_ON");
                            break;
                        case BluetoothAdapter.STATE_ON:
                            Log.e(TAG, "ENABLE_STATE_ON");
                            mCallback.onBluetooth(ENABLE_STATE_ON);
                            break;
                        case BluetoothAdapter.STATE_TURNING_OFF:
                            Log.e(TAG, "STATE_TURNING_OFF");
                            break;
                        case BluetoothAdapter.STATE_OFF:
                            Log.e(TAG, "ENABLE_STATE_OFF");
                            mCallback.onBluetooth(ENABLE_STATE_OFF);
                            break;
                    }
                    break;
                case BluetoothAdapter.ACTION_DISCOVERY_STARTED:
                    mCallback.onStartDiscovery();
                    break;
                case BluetoothAdapter.ACTION_DISCOVERY_FINISHED:
                    mCallback.onFinishDiscovery();
                    break;
                case BluetoothDevice.ACTION_FOUND:
                    int rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, (short) 30);
                    String name = intent.getStringExtra(BluetoothDevice.EXTRA_NAME);
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    mCallback.onDeviceFound(name, device, rssi, null);
                    break;
            }
        }
    };

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     *
     * @param socket The BluetoothSocket on which the connection was made
     * @param device The BluetoothDevice that has been connected
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device, UUID uuid) {
        Log.e(TAG, "connected, Socket uuid:" + uuid);

        // Cancel the thread that completed the connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();

        // TODO: 2017/3/24 uicallback connected
    }

    public void write(byte[] out) {
        // Create temporary object
        ConnectedThread r;
        // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (mState != STATE_CONNECTED) return;
            r = mConnectedThread;
        }
        // Perform the write unsynchronized
        r.write(out);
    }

    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private void connectionFailed() {
        mState = STATE_NONE;
        Log.e(TAG,"connected failed!");

        // TODO: 2017/3/24 uicallback connect failed

        // Start the service over to restart listening mode
        //this.start();
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private void connectionLost() {
        mState = STATE_NONE;
        Log.e(TAG,"connected lost!");
        // TODO: 2017/3/24 uicallback lost
        // Start the service over to restart listening mode
        //BluetoothChatService.this.start();
    }

    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        private UUID mmUUID;

        public ConnectThread(BluetoothDevice device, UUID uuid) {
            mmDevice = device;
            BluetoothSocket tmp = null;
            mmUUID = uuid;

            // Get a BluetoothSocket for a connection with the
            // given BluetoothDevice
            try {
                tmp = device.createInsecureRfcommSocketToServiceRecord(uuid);
            } catch (IOException e) {
                Log.e(TAG, "Socket Uuid: " + uuid.toString() + "create() failed", e);
            }
            mmSocket = tmp;
            mState = STATE_CONNECTING;
        }

        public void run() {
            Log.e(TAG, "BEGIN mConnectThread Socket Uuid:" + mmUUID + (mmSocket==null));
            setName("ConnectThread" + mmUUID.toString());

            // Always cancel discovery because it will slow down a connection
            mAdapter.cancelDiscovery();

            // Make a connection to the BluetoothSocket
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                Log.e(TAG, "connect mConnectThread Socket Uuid:" + mmUUID);
                mmSocket.connect();

            } catch (IOException e) {
                // Close the socket
                try {
                    mmSocket.close();
                    Log.e(TAG, "close mConnectThread Socket Uuid:" + mmUUID,e);
                } catch (IOException e2) {
                    Log.e(TAG, "unable to close() " + mmUUID +
                            " socket during connection failure", e2);
                }
                connectionFailed();
                return;
            }

            // Reset the ConnectThread because we're done
            synchronized (this) {
                mConnectThread = null;
            }

            // Start the connected thread
            connected(mmSocket, mmDevice, mmUUID);
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect " + mmUUID + " socket failed", e);
            }
        }
    }

    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            Log.e(TAG, "create ConnectedThread");
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
            mState = STATE_CONNECTED;
        }

        public void run() {
            Log.e(TAG, "BEGIN mConnectedThread");
            byte[] buffer = new byte[1024];
            int bytes;

            // Keep listening to the InputStream while connected
            while (mState == STATE_CONNECTED) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);

                    // Send the obtained bytes to the UI Activity
                    // TODO: 2017/3/24 uicallback oprate data
                    Log.e(TAG, Arrays.toString(buffer));
                } catch (IOException e) {
                    Log.e(TAG, "disconnected", e);
                    connectionLost();
                    break;
                }
            }
        }

        /**
         * Write to the connected OutStream.
         *
         * @param buffer The bytes to write
         */
        public void write(byte[] buffer) {
            try {
                mmOutStream.write(buffer);

                // Share the sent message back to the UI Activity

            } catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }

}
