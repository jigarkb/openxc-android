package com.openxc.interfaces.ble;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCallback;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.common.base.MoreObjects;
import com.openxc.interfaces.bluetooth.BluetoothException;
import com.openxc.interfaces.bluetooth.DeviceManager;
import com.openxc.sources.BytestreamDataSource;
import com.openxc.interfaces.VehicleInterface;
import com.openxc.sources.DataSourceException;
import com.openxc.sources.DataSourceResourceException;
import com.openxc.sources.SourceCallback;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Created by Srilaxmi on 9/1/17.
 */

public class BLEVehicleInterface extends BytestreamDataSource implements VehicleInterface {

    private BLEHelper mBLEHelper;
    private BluetoothGattCallback mGattCallback;
    private String mDeviceAddress;

    private final static String TAG = BLEVehicleInterface.class.getSimpleName();

    public BLEVehicleInterface(SourceCallback callback, Context context,
                               String address) throws DataSourceException {
        super(callback, context);

        LocalBroadcastManager.getInstance(context).registerReceiver(mBroadcastReceiver,
                new IntentFilter("ble-scan-complete"));

        try {
            mBLEHelper = new BLEHelper(context);
            mGattCallback = new GattCallback(mBLEHelper, context);
            mBLEHelper.setGattCallback(mGattCallback);
        } catch(BLEException e) {
            throw new DataSourceException(
                    "Unable to connect to BLE device", e);
        }

        setAddress(address);
        start();
    }

    public BLEVehicleInterface(Context context, String address)
            throws DataSourceException {
        this(null, context, address);
    }

    @Override
    public boolean isConnected() {
        boolean connected = false;
        connected = super.isConnected(); /*** ?? **/
        if (mBLEHelper.getConnectionState() == BLEHelper.STATE_CONNECTED)
            connected = true;
         else
            connected = false;

        return connected;
    }

    public boolean isConnecting() {
        boolean connecting = false;
        if (mBLEHelper.getConnectionState() == BLEHelper.STATE_CONNECTING)
            connecting = true;
        else
            connecting = false;

        return connecting;
    }

    @Override
    public boolean setResource(String otherAddress) throws DataSourceException {
        boolean reconnect = false;
        if (otherAddress == null || !sameResource(otherAddress, mDeviceAddress)) {
            reconnect = true;
        }

        setAddress(otherAddress);

        if (reconnect) setFastPolling(true);

        return reconnect;
    }


    private static boolean sameResource(String address, String otherAddress) {
        return otherAddress != null && otherAddress.equals(address);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("DeviceAddress", mDeviceAddress)
                .toString();
    }

    @Override
    protected int read(byte[] bytes) throws IOException {
        return 0;
    }

    @Override
    protected boolean write(byte[] bytes) {
        return false;
    }

    @Override
    protected void disconnect() {

    }

    @Override
    public synchronized void stop() { /** why is it synchronized? **/
        if(isRunning()) { //*** do we need this check?
            try {
                getContext().unregisterReceiver(mBroadcastReceiver);
            } catch(IllegalArgumentException e) {
                Log.w(TAG, "Broadcast receiver not registered but we expected it to be");
            }
            mBLEHelper.stop();
            super.stop();
        }
    }

    @Override
    protected void connect(){

        if(!isRunning()) return;

        BluetoothDevice lastConnectedDevice =
                mBLEHelper.getLastConnectedDevice();

        if(mDeviceAddress == null && lastConnectedDevice != null) {
            mDeviceAddress = lastConnectedDevice.getAddress();
        }

        if (!isConnected()) { /// *** is this check needed? What happens if it is already connected
            Log.i(TAG, "Connecting to Bluetooth device " + mDeviceAddress);
            try {
                mBLEHelper.connect(mDeviceAddress);
            }catch(BLEException e){
                Log.e(TAG, "Unable to connect to device " + mDeviceAddress, e);
            }
        }
    }

    private void setAddress(String address) throws DataSourceResourceException {
        if(address != null && !BluetoothAdapter.checkBluetoothAddress(address)) {
            throw new DataSourceResourceException("\"" + address +
                    "\" is not a valid MAC address");
        }
        mDeviceAddress = address;
    }

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Received BLE scan complete message");
            if (!isConnected() && !isConnecting()) {
                setFastPolling(true);
            }
        }
    };
}
