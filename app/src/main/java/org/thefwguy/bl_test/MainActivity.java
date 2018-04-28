package org.thefwguy.bl_test;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "TFG MainActivity";

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
    String bluetooth_device = "BLtest";

    // connectionState represent the status of the Bluetooth connection
    // 0 -> disconnected
    // 1 -> scan
    // 2 -> connect
    byte connectionState = 0;

    // Buttons and fields
    ToggleButton btn_connect;
    Button btn_send;
    TextView my_label;
    TextView idValue;
    EditText messageId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Assign buttons and toolbar

        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        btn_connect = findViewById(R.id.btn_connect);
        btn_send = findViewById(R.id.btn_send);
        my_label = findViewById(R.id.my_label);
        idValue = findViewById(R.id.IdValue);
        messageId = findViewById(R.id.messageId);

        // By default disable the Send button
        // Will eb enabled ONLY when connected to a device
        btn_send.setEnabled(false);


        if (!findBT()) {
            // BL does not exist - disable Connect and Send buttons
            my_label.setText("Bluetooth not present");
            btn_connect.setEnabled(false);

            Toast.makeText(this, "Bluetooth not present", Toast.LENGTH_SHORT).show();
        }
        else
        {
            my_label.setText("Bluetooth adapter available");
        }

        btn_connect.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    // The toggle is enabled
                    if (connectionState == 0) {
                        if (searchSlaveBT() == true) {
                            my_label.setText("Bluetooth Device Found");
                            connectionState = 1;
                            try {
                                openBT();
                                connectionState = 2;
                                // Enable the Send button
                                btn_send.setEnabled(true);

                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        } else {
                            my_label.setText("Bluetooth Device NOT Found");
                        }
                    }
                } else {
                    // The toggle is disabled
//                    if (connectionState == 2) {
                        try {
                            closeBT();
                            connectionState = 0;
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
  //                  }
                }
            }
        });

        btn_send.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Send the message via Bluetooth if connected - the button is disable UNLESS
                // a Bluetooth connection exists - no need to further check.
                try {
                     String msg = messageId.getText().toString();
                     sendDataBT(msg);
                } catch (IOException e) {
                     e.printStackTrace();
                }
            }
        });

//        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
//        fab.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
//                        .setAction("Action", null).show();
//            }
//        });
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        SharedPreferences sharedPref =
                PreferenceManager.getDefaultSharedPreferences(this);

        bluetooth_device = sharedPref.getString (SettingsActivity.KEY_PREF_DEVICE_NAME, "No setting");
        idValue.setText(bluetooth_device);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        }

        return  super.onOptionsItemSelected(item);
    }

    // ---------------- Bluetooth functions -----------------------------------------

    boolean findBT() {
        Log.d(TAG, "findBT");
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            return false;
        }
        return true;
    }

    boolean searchSlaveBT() {
        Log.d(TAG, "searchSlaveBT");
        if(!mBluetoothAdapter.isEnabled())
        {
            Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBluetooth, 0);
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

    void openBT() throws IOException
    {
        Log.d(TAG, "openBT");

        my_label.setText("Bluetooth Waiting connection");

        UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); //Standard SerialPortService ID

        try {
            mmSocket = mmDevice.createInsecureRfcommSocketToServiceRecord(uuid);
        } catch (IOException e) {
            Log.d(TAG, "createInsecureRfcomm - " + e.getMessage());
        }

        mBluetoothAdapter.cancelDiscovery();

        try {
            mmSocket.connect();     // Waiting here ?
        } catch (IOException e) {
            Log.d(TAG, "connect - " + e.getMessage());
            e.printStackTrace();
            my_label.setText("Bluetooth abort connection");
            return;
        }

        mmOutputStream = mmSocket.getOutputStream();
        mmInputStream = mmSocket.getInputStream();

        my_label.setText("Bluetooth Opened");

        beginListenForData();
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
                                            my_label.setText(data);
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
        my_label.setText("Data Sent");
    }

    void closeBT() throws IOException
    {
        Log.d(TAG, "closeBT");
        stopWorker = true;
        if (mmOutputStream != null) mmOutputStream.close();
        if (mmInputStream != null) mmInputStream.close();
        if (mmSocket != null) mmSocket.close();
        my_label.setText("Bluetooth Closed");
    }


}
