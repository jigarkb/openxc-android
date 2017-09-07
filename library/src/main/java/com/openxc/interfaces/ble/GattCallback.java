package com.openxc.interfaces.ble;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.UUID;

import static com.openxc.interfaces.ble.BLEHelper.STATE_CONNECTED;
import static com.openxc.interfaces.ble.BLEHelper.STATE_DISCONNECTED;

/**
 * Created by Srilaxmi on 8/30/17.
 */
@TargetApi(18)
public class GattCallback extends BluetoothGattCallback {

    public static final String C5_OPENXC_BLE_SERVICE_UUID = "6800-d38b-423d-4bdb-ba05-c9276d8453e1";
    public static final String C5_OPENXC_BLE_CHARACTERISTIC_WRITE_UUID = "6800-d38b-5262-11e5-885d-feff819cdce2";
    public static final String C5_OPENXC_BLE_CHARACTERISTIC_NOTIFY_UUID = "6800-d38b-5262-11e5-885d-feff819cdce3";
    public static final String C5_OPENXC_BLE_DESCRIPTOR_NOTIFY_UUID = "00002902-0000-1000-8000-00805f9b34fb";


    private final static String TAG = "GattCallback.class.getSimpleName()";
    private BluetoothGatt mBluetoothGatt;
    private BLEHelper mBLEHelper;
    private Context mContext;

    private byte[] data;

    public GattCallback(BLEHelper bleHelper, Context context){

        mBLEHelper = bleHelper;
        mBluetoothGatt = bleHelper.getBluetoothGatt();
        mContext = context;
    }

    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        String intentAction;
        if (newState == BluetoothProfile.STATE_CONNECTED) {

            mBLEHelper.setConnectionState(STATE_CONNECTED);
            Log.i(TAG, "Connected to GATT server.");

            //Stop scanning for BLE devices as we are connected
            mBLEHelper.scanLeDevice(false);

            // Attempts to discover services after successful connection.
            Log.i(TAG, "Attempting to start service discovery:" +
                    mBluetoothGatt.discoverServices());

        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            mBLEHelper.setConnectionState(STATE_DISCONNECTED);
            Log.i(TAG, "Disconnected from GATT server.");
        }
    }


        @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            mBluetoothGatt.getService(uuid);// ***check for null
            getCharacteristic(uuid);
            setCharacteristicNotification();
        } else {
            Log.w(TAG, "onServicesDiscovered received: " + status);
        }
    }

    @Override
    public void onCharacteristicRead(BluetoothGatt gatt,
                                     BluetoothGattCharacteristic characteristic,
                                     int status) {
        Log.i("CharacteristicRead", characteristic.toString());
        if (status == BluetoothGatt.GATT_SUCCESS) {

        }
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt,
                                        BluetoothGattCharacteristic characteristic) {
        readChangedCharacteristic(characteristic);
    }

    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled If true, enable notification.  False otherwise.
     */
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enabled) {
        if (mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothGatt not initialized");
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);

        // This is specific to Heart Rate Measurement.
        //if (UUID_HEART_RATE_MEASUREMENT.equals(characteristic.getUuid())) {
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                UUID.fromString(SampleGattAttributes.NOTIFY_CHARACTERISTIC));
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        mBluetoothGatt.writeDescriptor(descriptor);
        //}
    }

    public void readChangedCharacteristic( BluetoothGattCharacteristic characteristic){
        data = characteristic.getValue(); // *** this is going to get overwritten by next call, so make a queue
        if (data != null && data.length > 0) {
            final StringBuilder stringBuilder = new StringBuilder(data.length);
            for(byte byteChar : data)
                stringBuilder.append(String.format("%02X ", byteChar));
            Log.i(TAG, "Data: " + new String(data));
        }
    }

}
