package org.thefwguy.bl_test;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
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
import java.lang.reflect.InvocationTargetException;

import static android.support.v4.app.ActivityCompat.startActivityForResult;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "TFG MainActivity";

    protected BluetoothManager bManager = new BluetoothManager();

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
        Log.d(TAG, "onCreate");
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

        if (!bManager.localInitBT()) {
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
                Log.d(TAG, "btn_connect hit");

                if (isChecked) {
                    // The toggle is enabled
                    if (connectionState == 0) {
                        btn_connect.setTextOff("Connecting");

                        if (bManager.searchSlaveBT() == true) {
                            my_label.setText("Bluetooth Device Found");
                            connectionState = 1;

                            try {
                                if (bManager.openBT() ) {
                                    my_label.setText("Bluetooth Opened");
                                    connectionState = 2;
                                    // Enable the Send button
                                    btn_send.setEnabled(true);
                                } else {
                                    bManager.discoverBT();

                                    btn_connect.setTextOff("Connect");
                                    btn_connect.toggle();
                                    connectionState=0;
                                    my_label.setText("Bluetooth connection failed");
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            } catch (NoSuchMethodException e) {
                                e.printStackTrace();
                            } catch (IllegalAccessException e) {
                                e.printStackTrace();
                            } catch (InvocationTargetException e) {
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
                            bManager.closeBT();
                            my_label.setText("Bluetooth Closed");
                            // Disable the Send button
                            btn_send.setEnabled(false);

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
                Log.d(TAG, "btn_send hit");
                // Send the message via Bluetooth if connected - the button is disabled UNLESS
                // a Bluetooth connection exists - no need to further check.
                try {
                     String msg = messageId.getText().toString();
                     bManager.sendDataBT(msg);
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

        bManager.bluetooth_device = sharedPref.getString (SettingsActivity.KEY_PREF_DEVICE_NAME, "No setting");
        idValue.setText(bManager.bluetooth_device);
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
}
