package org.thefwguy.bl_test;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;

public class BluetoothManager {
    private static final String TAG = "TFG BluetoothManager";

    BluetoothAdapter mBluetoothAdapter;
    BluetoothSocket mmSocket = null;
    BluetoothDevice mmDevice = null;
    OutputStream mmOutputStream = null;
    InputStream mmInputStream = null;

    Thread workerThread;
    byte[] readBuffer;
    int readBufferPosition;
    int counter;
    volatile boolean stopWorker;
    // Device to pair
    String bluetooth_device = "BLtest";     // String updated from settings
    UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); //Standard SerialPortService ID

    boolean findBT() {
        Log.d(TAG, "findBT");
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            return false;
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

    boolean searchSlaveBT() {
        Log.d(TAG, "searchSlaveBT");
        if(!mBluetoothAdapter.isEnabled())
        {
            Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
//            startActivityForResult(enableBluetooth, 0);
        }

        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if(pairedDevices.size() > 0)
        {
            for(BluetoothDevice device : pairedDevices)
            {
                if(device.getName().equals(bluetooth_device))
                {
                    mmDevice = device;
                    return true;
                }
            }
        }
        return false;
    }

    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
        try {
            final Method m = device.getClass().getMethod("createInsecureRfcommSocketToServiceRecord", new Class[] { UUID.class });
            return (BluetoothSocket) m.invoke(device, uuid);
        } catch (Exception e) {
            Log.e(TAG, "Could not create Insecure RFComm Connection",e);
            return  device.createRfcommSocketToServiceRecord(uuid);
        }
    }

    boolean openBT() throws IOException
    {
        Log.d(TAG, "openBT");

//        my_label.setText("Bluetooth Waiting connection");

        try {
            mmSocket = createBluetoothSocket(mmDevice);
        } catch (IOException e1) {
            Log.e(TAG, "createBluetoothSocket - " + e1);
            return false;
        }

//        try {
//            mmSocket = mmDevice.createInsecureRfcommSocketToServiceRecord(uuid);
//        } catch (IOException e) {
//            Log.d(TAG, "createInsecureRfcomm - " + e.getMessage());
//        }

        mBluetoothAdapter.cancelDiscovery();

        try {
            mmSocket.connect();     // Waiting here ?
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
}
