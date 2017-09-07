package com.openxc.interfaces.ble;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.openxc.VehicleManager;
import com.openxc.interfaces.bluetooth.BluetoothVehicleInterface;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Srilaxmi on 8/30/17.
 */

public class BLEHelper {

    private final static String TAG = BLEHelper.class.getSimpleName();
    private BluetoothAdapter mBluetoothAdapter;
    private static final long SCAN_PERIOD = 10000;
    private BluetoothLeScanner mLEScanner = null;
    private ScanSettings settings;
    private List<ScanFilter> filters;
    private Context mContext;
    private Handler mHandler;
    private ScanCallback mScanCallback;
    private BluetoothAdapter.LeScanCallback mLeScanCallback;

    public static final int STATE_DISCONNECTED = 0;
    public static final int STATE_CONNECTING = 1;
    public static final int STATE_CONNECTED = 2;

    public static final String DEVICE_NAME_PREFIX = "OpenXC";

    private int mConnectionState = STATE_DISCONNECTED;

    private String mBluetoothDeviceAddress;
    private BluetoothDevice mBluetoothDevice;
    private BluetoothGatt mBluetoothGatt;
    private BluetoothGattCallback mGattCallback;

    public BLEHelper(Context context) throws BLEException {

        mContext = context;
        mHandler = new Handler();

        if(initialize() == null) {
            String message = "This device most likely does not have " +
                    "a Bluetooth adapter";
            Log.w(TAG, message);
            throw new BLEException(message);
        } else {
            Log.d(TAG, "Initializing Bluetooth(BLE) device manager");
        }

    }


    /**
     * Checks if BLE is supported on device
     *
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return reference to Bluetooth Adapter if the initialization is successful, null if not
     */

    public BluetoothAdapter initialize() {

        if(mBluetoothAdapter == null) {

            if (!mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
                return null;
            }

            // work around an Android bug, requires that this is called before
            // getting the default adapter
            if(Looper.myLooper() == null) {
                Looper.prepare();
            }
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        }

        return mBluetoothAdapter;
    }


    /**
     * Checks if BLE is enabled on device
     * Bluetooth adapter needs to be initialized, or it will return false
     *
     * @return Return true if enabled false if not enabled
     */
    public boolean isBLEEnabled(){

        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            return false;
        }
        return true;
    }

    /**
     * Initializes parameters required for starting scan in Apis > 21
     */

    public void initializeScanner(){
        if(mBluetoothAdapter == null) {
            Log.i(TAG, "Bluetooth Adapter is null");
            return;
        }
        if (Build.VERSION.SDK_INT >= 21) {
            mLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
            settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();
            filters = new ArrayList<ScanFilter>();
        }
    }

    /**
     *
     * @param enable
     */
    public void scanLeDevice(final boolean enable) {
        if(mBluetoothAdapter == null) {
            Log.i(TAG, "Bluetooth Adapter is null");
            return;
        }
        createCallBackForApi21();
        createCallBackForApi18();
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    sendScanCompleteBroadcast();
                    if (Build.VERSION.SDK_INT < 21) {
                        mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    } else {
                        mLEScanner.stopScan(mScanCallback);

                    }
                }
            }, SCAN_PERIOD);
            if (Build.VERSION.SDK_INT < 21) {
                mBluetoothAdapter.startLeScan(mLeScanCallback);
            } else {
                initializeScanner();
                if(mLEScanner != null) {
                    mLEScanner.startScan(filters, settings, mScanCallback);
                }
            }
        } else {
            if (Build.VERSION.SDK_INT < 21) {
                mBluetoothAdapter.stopLeScan(mLeScanCallback);
            } else {
                mLEScanner.stopScan(mScanCallback);
            }
        }
    }

    private void sendScanCompleteBroadcast() {
        Log.i(TAG, "Broadcasting scan complete message");
        Intent intent = new Intent("ble-scan-complete");
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);
    }

    @TargetApi(21)
    private void createCallBackForApi21() {

        if (Build.VERSION.SDK_INT >= 21) {

            mScanCallback = new ScanCallback() {

                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    Log.i("callbackType", String.valueOf(callbackType));
                    Log.i("result", result.toString());

                    if (result.getScanRecord().getDeviceName().toLowerCase().contains("OpenXC".toLowerCase())) {
                        mBluetoothDevice = result.getDevice();
                    }

                    //***Add device to list of devices


                }

                @Override
                public void onBatchScanResults(List<ScanResult> results) {
                    for (ScanResult sr : results) {
                        Log.i("ScanResult - Results", sr.toString());
                    }
                }

                @Override
                public void onScanFailed(int errorCode) {
                    Log.e("Scan Failed", "Error Code: " + errorCode);
                }
            };
        }
    }

    @TargetApi(18)
    private void createCallBackForApi18() {

        if (Build.VERSION.SDK_INT < 21 && Build.VERSION.SDK_INT > 18) {
            mLeScanCallback =
                    new BluetoothAdapter.LeScanCallback() {

                        @Override
                        public void onLeScan(final BluetoothDevice device, int rssi,
                                             byte[] scanRecord) {
                            Log.i("onLeScan", device.toString());

                            if (device.getName().toLowerCase().contains("OpenXC".toLowerCase())) {
                                mBluetoothDevice = device;
                            }

                            //***Add device to list of devices
                        }

                    };
        }
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     *
     * @return Return true if the connection is initiated successfully. The connection result
     *         is reported asynchronously through the
     *         {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     *         callback.
     */
    @TargetApi(18)
    public boolean connect(String address) throws BLEException {
        if(Build.VERSION.SDK_INT < 18){
            Log.i(TAG, "BLE not supported on API Version < 18");
            throw new BLEException("BLE not supported on API Version < 18");
        }

        if (mBluetoothAdapter == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            throw new BLEException("BluetoothAdapter not initialized");
        }

        if (address == null){
            address = mBluetoothDeviceAddress;
        }

        if (address ==  null){
            Log.w(TAG, "Unspecified device address.");
            throw new BLEException("Unspecified device address.");
        }

        // Previously connected device.  Try to reconnect.
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) { //*** This will autoconnect?
                mConnectionState = STATE_CONNECTING;
                return true;
            } else {
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            throw new BLEException("Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        if (mGattCallback == null){
            Log.i(TAG, "GattCallback not instantiated");
            throw new BLEException("GattCallback not instantiated");
            return false;
        }
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;//*** should this be set after connected?
        mConnectionState = STATE_CONNECTING;
        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    @TargetApi(18)
    public void disconnect() {
        if(Build.VERSION.SDK_INT < 18){
            Log.i(TAG, "BLE not supported on API Version < 18");
            return;
        }
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    @TargetApi(18)
    public void close() {
        if(Build.VERSION.SDK_INT < 18){
            Log.i(TAG, "BLE not supported on API Version < 18");
            return;
        }
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    public void stop(){

    }

    public void setGattCallback(BluetoothGattCallback bluetoothGattCallback){
        mGattCallback = bluetoothGattCallback;
    }

    public BluetoothGatt getBluetoothGatt(){
        return mBluetoothGatt;
    }

    public void setConnectionState(int state){
        mConnectionState = state;
    }

    public int getConnectionState(){
        return mConnectionState;
    }

    public BluetoothDevice getBluetoothDevice(){
        return mBluetoothDevice;
    }

    public void storeLastConnectedDevice(BluetoothDevice device) {
        /***/
    }

    public BluetoothDevice getLastConnectedDevice() {
        /***/
    }

}
