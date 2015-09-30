package example.bleexample;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import mouthpiece.eddystone.Advertiser;
import mouthpiece.peripheral.MouthPiecePeripheral;
import mouthpiece.peripheral.MouthPieceService;
import mouthpiece.peripheral.WriteRequest;
import mouthpiece.peripheral.WriteResponse;
import mouthpiece.peripheral.annotation.OnWrite;
import mouthpiece.peripheral.annotation.ResponseNeeded;


public class MainActivity extends Activity {

    private static final String TAG = MainActivity.class.getSimpleName();

    private static final String SERVICE_UUID = "7F93D614-920A-48B0-8910-B3694E06E0FA";
    private static final String CHANNEL_UUID = "06AFE76A-7859-4D78-B918-035AA960ED56";

    private List<Button> buttons = new ArrayList<Button>();
    private WebView webView;
    private TextView stateText;

    private static final String BROADCAST_URL = "http://litelite.net/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        int buttonIds[] = {
                R.id.channel_button_01,
                R.id.channel_button_02,
                R.id.channel_button_03,
                R.id.channel_button_04,
                R.id.channel_button_05,
                R.id.channel_button_06,
                R.id.channel_button_07,
                R.id.channel_button_08,
                R.id.channel_button_09,
                R.id.channel_button_10,
                R.id.channel_button_11,
                R.id.channel_button_12

        };

        webView = (WebView)findViewById(R.id.screen);
        webView.setWebViewClient(new WebViewClient());
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);

        stateText = (TextView)findViewById(R.id.rc_state_text);

        for (int i = 0; i < buttonIds.length; i++) {
            int channelButtonId = buttonIds[i];
            Button b = (Button)findViewById(channelButtonId);
            buttons.add(b);
            final int channel = i;
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    onChannelSelected(channel);
                }
            });
        }

        onChannelSelected(0);

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

    private int mCurrentChannel = 0;

    private void onChannelSelected(final int channel) {

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (channel > 12) {
                    return;
                }
                mCurrentChannel = channel;
                for (int i = 0; i < buttons.size(); i++) {
                    Button b = buttons.get(i);
                    if (i == channel) {
                        b.setBackgroundColor(Color.parseColor("#ffd700"));
                    } else {
                        b.setBackgroundColor(Color.parseColor("#009999"));
                    }
                }
                showPage(channel);
            }
        });
    }

    private void showPage(int channel) {
        if (channel > 12) {
            return;
        }
        String[] urls = getResources().getStringArray(R.array.page_urls);
        String url = urls[channel];
        webView.loadUrl(url);
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

            @OnWrite(CHANNEL_UUID)
            @ResponseNeeded(false)
            public void SetDestination(WriteRequest req, WriteResponse res) {
                int v = req.getIntValue();
                Log.d(TAG, "Destination:" + String.valueOf(v));
                int channel = v - 1;
                onChannelSelected(channel);
            }
        };
        return service;
    }

    @Override
    protected void onStop() {
        stopBLE();
        super.onStop();
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
        if (id == R.id.action_ac) {
            Intent i = new Intent(this, ACActivity.class);
            startActivity(i);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
