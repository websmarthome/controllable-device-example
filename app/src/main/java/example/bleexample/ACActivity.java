package example.bleexample;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import mouthpiece.eddystone.Advertiser;
import mouthpiece.peripheral.MouthPiecePeripheral;
import mouthpiece.peripheral.MouthPieceService;
import mouthpiece.peripheral.ReadRequest;
import mouthpiece.peripheral.ReadResponse;
import mouthpiece.peripheral.WriteRequest;
import mouthpiece.peripheral.WriteResponse;
import mouthpiece.peripheral.annotation.Notifiable;
import mouthpiece.peripheral.annotation.OnRead;
import mouthpiece.peripheral.annotation.OnWrite;
import mouthpiece.peripheral.annotation.ResponseNeeded;
import mouthpiece.utils.ValueTypeConverter;


public class ACActivity extends Activity {

    private static final String TAG = ACActivity.class.getSimpleName();

    private static final String SERVICE_UUID = "405E7866-A0CB-4006-B75B-2796687D8FBD";
    private static final String CURRENT_UUID = "573B455B-6E54-4BD4-B63A-8C5DF4713746";
    private static final String SETTING_UUID = "02A7DBEE-7AE0-4CDB-A3CE-A2F3E6944C90";
    private static final String EDITOR_UUID  = "5D0FEA1B-A2FC-4E41-9D85-FC76048C8CE1";

    // INDICATOR: 5D0FEA1B-A2FC-4E41-9D85-FC76048C8CE1 0: DOWN 1: UP

    private static final int DEFAULT_TEMPERATURE = 23;
    private static final int MAX_TEMPERATURE = 32;
    private static final int MIN_TEMPERATURE = 16;

    private static final int DEFAULT_INTERVAL = 1000;

    private Button settingTemperatureView;
    private Button currentTemperatureView;
    private TextView stateText;

    private int settingTemperature = DEFAULT_TEMPERATURE;
    private int currentTemperature = DEFAULT_TEMPERATURE;

    private Timer intervalTimer;
    private int intervalMillis = DEFAULT_INTERVAL;

    private static final String BROADCAST_URL = "http://goo.gl/tq6vJy"; // http://litelite.net/ac.html";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ac);

        settingTemperatureView = (Button)findViewById(R.id.setting_temperature);
        currentTemperatureView = (Button)findViewById(R.id.current_temperature);
        stateText = (TextView)findViewById(R.id.rc_state_text);

        findViewById(R.id.setting_temperature_up).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                incrementSettingTemperature();
            }
        });

        findViewById(R.id.setting_temperature_down).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                decrementSettingTemperature();
            }
        });

        updateCurrentTemperatureView(currentTemperature);
        updateSettingTemperatureView(settingTemperature);

        findViewById(R.id.start_ble_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startBLE();
            }
        });

        findViewById(R.id.stop_ble_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stopBLE();
            }
        });
    }

    private Advertiser advertiser;

    private void startEddystone() {
        if (advertiser == null) {
            advertiser = new Advertiser(this);
        }

        if (advertiser.systemSupported()
                && advertiser.isEnabled()
                && advertiser.isReady()) {
            advertiser.start(BROADCAST_URL);
        }
    }

    private void stopEddystone() {
        if (advertiser != null & !advertiser.isReady()) {
            advertiser.stop();
        }
    }

    private void startBLE() {
        if (peripheral != null && peripheral.isRunning())
            return;

        stateText.setText("Remote Controller Enabled");
        stateText.setTextColor(Color.parseColor("#ff8c00"));
        startBLEController();
        startEddystone();
    }

    private void stopBLE() {
        stateText.setText("Remote Controller Disabled");
        stateText.setTextColor(Color.parseColor("#666666"));
        if (peripheral == null)
            return;

        if (peripheral.isRunning()) {
            peripheral.stop();
        }
        stopEddystone();
    }

    private MouthPiecePeripheral peripheral;

    private void startBLEController() {
        Log.d(TAG, "startBLEController");
        peripheral = MouthPiecePeripheral.build(this, createService());
        peripheral.start();
    }

    private MouthPieceService createService() {
        MouthPieceService service = new MouthPieceService(SERVICE_UUID) {
            @OnWrite(EDITOR_UUID)
            @ResponseNeeded(false)
            public void ModifySettingTemperature(WriteRequest req, WriteResponse res) {
                int v = req.getIntValue();
                if (v == 0) {
                    decrementSettingTemperature();
                } else if (v == 1) {
                    incrementSettingTemperature();
                }
            }

            @OnRead(CURRENT_UUID)
            @Notifiable(true)
            public void ReadCurrentTemperature(ReadRequest req, ReadResponse res) {
                res.writeInt(currentTemperature);
            }

            @OnRead(SETTING_UUID)
            @Notifiable(true)
            public void readSettingTemperature(ReadRequest req, ReadResponse res) {
                res.writeInt(settingTemperature);
            }
        };

        return service;
    }

    @Override
    protected void onResume() {
        super.onResume();
        startIntervalTimer();
    }

    @Override
    protected void onStop() {
        stopIntervalTimer();
        stopBLE();
        super.onStop();
    }

    private void updateCurrentTemperature() {
        Random r = new Random();
        int n = r.nextInt(3);
        if (n == 1) {
            incrementCurrentTemperature();
        } else if (n == 2) {
            decrementCurrentTemperature();
        }
        if (peripheral != null && peripheral.isRunning()) {
            byte[] value = ValueTypeConverter.bytesFromInt(currentTemperature);
            peripheral.updateValue(SERVICE_UUID, CURRENT_UUID, value);
        }
    }

    private void startIntervalTimer() {
        intervalTimer = new Timer();
        intervalTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                updateCurrentTemperature();
                stopIntervalTimer();
                startIntervalTimer();
            }
        }, intervalMillis);
    }

    private void stopIntervalTimer() {
        if (intervalTimer != null) {
            intervalTimer.cancel();
            intervalTimer = null;
        }
    }

    private void incrementCurrentTemperature() {
        if (currentTemperature >= MAX_TEMPERATURE)
            return;
        currentTemperature++;
        updateCurrentTemperatureView(currentTemperature);

    }

    private void decrementCurrentTemperature() {
        if (currentTemperature <= MIN_TEMPERATURE)
            return;
        currentTemperature--;
        updateCurrentTemperatureView(currentTemperature);

    }

    private void notifySettingTemperature() {
        if (peripheral != null && peripheral.isRunning()) {
            byte[] value = ValueTypeConverter.bytesFromInt(settingTemperature);
            Log.d(TAG, "notify setting:" + String.valueOf(settingTemperature));
            peripheral.updateValue(SERVICE_UUID, SETTING_UUID, value);
        }
    }

    private void incrementSettingTemperature() {
        if (settingTemperature >= MAX_TEMPERATURE)
            return;
        settingTemperature++;
        updateSettingTemperatureView(settingTemperature);
        notifySettingTemperature();
    }

    private void decrementSettingTemperature() {
        if (settingTemperature <= MIN_TEMPERATURE)
            return;
        settingTemperature--;
        updateSettingTemperatureView(settingTemperature);
        notifySettingTemperature();
    }

    private void updateCurrentTemperatureView(final int t) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                currentTemperatureView.setText(String.valueOf(t));
            }
        });
    }

    private void updateSettingTemperatureView(final int t) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                settingTemperatureView.setText(String.valueOf(t));
            }
        });
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_ac, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_viewer) {
            Intent i = new Intent(this, MainActivity.class);
            startActivity(i);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
