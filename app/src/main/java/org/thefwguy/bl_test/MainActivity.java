package org.thefwguy.bl_test;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    BluetoothAdapter mBluetoothAdapter;
    BluetoothSocket mmSocket;
    BluetoothDevice mmDevice;
    OutputStream mmOutputStream;
    InputStream mmInputStream;

    Thread workerThread;
    byte[] readBuffer;
    int readBufferPosition;
    int counter;
    volatile boolean stopWorker;
    // Device to pair
    String bluetooth_device = "NiRis";

    // connectionState represent the status of the Bluetooth connection
    // 0 -> disconnected
    // 1 -> scan
    // 2 -> connect
    byte connectionState = 0;

    // Buttons and fields
    ToggleButton btn_connect;
    Button btn_send;
    TextView my_label;

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

        if (!findBT()) {
            // BL does not exist - disable Connect button
            btn_connect.setEnabled(false);

            Toast.makeText(this, "Bluetooth not present", Toast.LENGTH_SHORT).show();
        }
        else
        {
            my_label.setText("Bluetooth adapter available");
        }

        btn_send.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Send the message viw Bluetooth
                if (connectionState == 2)
                {
                    // Code here executes on main thread after user presses button
                    try {
                        sendData("Message test");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        btn_connect.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    if (connectionState == 0) {
                        if (searchSlaveBT() == true) {
                            connectionState = 1;
                            try {
                                openBT();
                                connectionState = 2;
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    // The toggle is enabled
                } else {
                    if (connectionState == 2) {
                        try {
                            closeBT();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    // The toggle is disabled
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
    }









    // ---------------- Bluetooth functions -----------------------------------------

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
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    boolean findBT() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            my_label.setText("No bluetooth adapter available");
            return false;
        }
        return true;
    }

    boolean searchSlaveBT() {
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
                    my_label.setText("Bluetooth Device Found");
                    return true;
                }
            }
        }
        my_label.setText("Bluetooth Device NOT Found");
        return false;
    }

    void openBT() throws IOException
    {
        my_label.setText("Open Bluetooth");

        UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); //Standard SerialPortService ID
        mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid);
        mmSocket.connect();
        mmOutputStream = mmSocket.getOutputStream();
        mmInputStream = mmSocket.getInputStream();

        beginListenForData();

        my_label.setText("Bluetooth Opened");
    }

    void beginListenForData()
    {
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

    void sendData(String msg) throws IOException
    {
//        String msg = messageId.getText().toString();
        msg += "\n";
        mmOutputStream.write(msg.getBytes());
        my_label.setText("Data Sent");
    }

    void closeBT() throws IOException
    {
        stopWorker = true;
        mmOutputStream.close();
        mmInputStream.close();
        mmSocket.close();
        my_label.setText("Bluetooth Closed");
    }


}
