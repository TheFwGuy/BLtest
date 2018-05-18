package org.thefwguy.bl_test;

import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Build;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class BluetoothManager {
    private static final String TAG = "TFG BluetoothManager";

    Application mApp;

    private BluetoothAdapter mBluetoothAdapter;
    private ArrayList<BluetoothDevice> mDevicesList;
    private BluetoothSocket mmSocket = null;
    private BluetoothDevice mmDevice = null;
    private ParcelUuid[] mmDeviceUUIDs = null;
    private OutputStream mmOutputStream = null;
    private InputStream mmInputStream = null;

    private Thread workerThread;
    private byte[] readBuffer;
    private int readBufferPosition;
    private int counter;
    protected volatile boolean stopWorker = true;
    // Device to pair
    protected String bluetooth_device = "BLtest";     // String updated from settings
    private UUID uuid = UUID.fromString("0000110e-0000-1000-8000-00805f9b34fb"); //Standard SerialPortService ID

    // The function looks if exists a Bluetooth interface on the terminal, if so
    // open it, assign to the pointer and return true.
    // After that the main access to Bluetooth will be via mBluetoothAdapter variable
    // Called on onCreate - thus call this method only once !

    boolean localInitBT() {
        Log.d(TAG, "localInitBT");
        mDevicesList = new ArrayList<BluetoothDevice>();        // Init the list
        // Look here to populate : https://stackoverflow.com/questions/17763779/android-bluetooth-cant-connect

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            return false;
        }

        // Check permissions !  On newer Android version is not enough to declare the use
        // of resources in the manifest but is needed to enable it explicitly
        if(!mBluetoothAdapter.isEnabled()) {
            mBluetoothAdapter.enable();
//            Log.d(TAG, "Request enable Bluetooth (TBD)");
//            Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
//            startActivityForResult(enableBluetooth, 0);
        }

        return true;
    }

    boolean discoverBT() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (mBluetoothAdapter.startDiscovery()) {
            Log.d(TAG, "Discovering other Bluetooth devices ...");
            //If discovery has started, then display the following toast....//
//            Toast.makeText(getApplicationContext(), "Discovering other bluetooth devices...",
//                    Toast.LENGTH_SHORT).show();
        } else {
            Log.d(TAG, "Discovery failed");
            //If discovery hasn’t started, then display this alternative toast//
//            Toast.makeText(getApplicationContext(), "Something went wrong! Discovery has failed to start.",
//                    Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    // The function is looking for a specific device
    boolean searchSlaveBT() {
        Log.d(TAG, "searchSlaveBT - looking for " + bluetooth_device);

        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if(pairedDevices.size() > 0)
        {
            for(BluetoothDevice device : pairedDevices)
            {
                if(device.getName().equals(bluetooth_device))
                {
                    mmDevice = device;
                    if (mmDevice.fetchUuidsWithSdp()) {
                        mmDeviceUUIDs = mmDevice.getUuids();
                    }
                    Log.d(TAG, "searchSlaveBT - found " + bluetooth_device + " - uses UUID " + mmDeviceUUIDs[0].toString());
                    // Overwrite default UUID
                    uuid = UUID.fromString(mmDeviceUUIDs[0].toString());
                    return true;
                }
            }
        }
        return false;
    }

    boolean openBT() throws IOException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Log.d(TAG, "openBT");

        Log.d(TAG, "cancel discovery");
        mBluetoothAdapter.cancelDiscovery();

        try {
            mmSocket = createBluetoothSocket(mmDevice);
        } catch (IOException e1) {
            Log.e(TAG, "createBluetoothSocket - " + e1);
            return false;
        }

        // Debug info
        Log.d(TAG, "Connected ? : " + mmSocket.isConnected());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Log.d(TAG, "Connection type ? : " + mmSocket.getConnectionType());
        }
        // Debug info

        try {
            if(!mmSocket.isConnected()) {
                Log.d(TAG, "Not Connected - force connect");
                mmSocket.connect();
            }
        } catch (IOException e) {
            Log.e(TAG, "connect - " + e.getMessage());
            e.printStackTrace();
            return false;
        }

        mmOutputStream = mmSocket.getOutputStream();
        mmInputStream = mmSocket.getInputStream();

        Log.d(TAG, "Open BT socket");
        beginListenForData();
        return true;
    }

    void beginListenForData()
    {
        Log.d(TAG, "beginListenForData");
        final Handler handler = new Handler();
        final byte delimiter = 10; //This is the ASCII code for a newline character

        stopWorker = false;
        readBufferPosition = 0;
        readBuffer = new byte[1024];

        workerThread = new Thread(new Runnable()
        {
            public void run()
            {
                while(!Thread.currentThread().isInterrupted() && !stopWorker)
                {
                    try
                    {
//                        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();

                        if(!mmSocket.isConnected()) {
                            Log.d(TAG, "Detected disconnection !");
                            stopWorker = true;
                        }

                        int bytesAvailable = mmInputStream.available();
                        if(bytesAvailable > 0)
                        {
                            byte[] packetBytes = new byte[bytesAvailable];
                            mmInputStream.read(packetBytes);
                            for(int i=0;i<bytesAvailable;i++)
                            {
                                byte b = packetBytes[i];
                                if(b == delimiter)
                                {
                                    byte[] encodedBytes = new byte[readBufferPosition];
                                    System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                    final String data = new String(encodedBytes, "US-ASCII");
                                    readBufferPosition = 0;

                                    handler.post(new Runnable()
                                    {
                                        public void run()
                                        {
                                            // TBD - save received messages in an array and assign the array to a view
//                                            my_label.setText(data);
                                            Log.d(TAG, "Received data : " + data.toString());
                                        }
                                    });
                                }
                                else
                                {
                                    readBuffer[readBufferPosition++] = b;
                                }
                            }
                        }
                    }
                    catch (IOException ex)
                    {
                        stopWorker = true;
                    }
                }

                Log.d(TAG, "Out from the loop ! Connection lost");
                try {
                    closeBT();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        workerThread.start();
    }

    void sendDataBT(String msg) throws IOException
    {
        Log.d(TAG, "sendDataBT");
        msg += "\n";
        mmOutputStream.write(msg.getBytes());
    }

    void closeBT() throws IOException
    {
        Log.d(TAG, "closeBT");
        stopWorker = true;
        if (mmOutputStream != null) mmOutputStream.close();
        if (mmInputStream != null) mmInputStream.close();
        if (mmSocket != null) mmSocket.close();
    }

    // --------  Private functions ---------

    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
        try {
            return (BluetoothSocket) device.getClass().getMethod("createRfcommSocket", new Class[] {int.class}).invoke(device,1);
        } catch (Exception e) {
            Log.e(TAG, "Could not create Insecure RFComm Connection",e);
            return  device.createRfcommSocketToServiceRecord(uuid);
        }
    }
}
