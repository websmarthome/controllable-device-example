/*
* Copyright 2015 Lyo Kato (lyo.kato@gmail.com)
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package mouthpiece.eddystone;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.pm.PackageManager;

import mouthpiece.eddystone.frame.URLFrame;

public class Advertiser {

    public static final String TAG = Advertiser.class.getSimpleName();

    public interface Listener {
        public void onAdvertiseSuccess(AdvertiseSettings settingsInEffect);
        public void onAdvertiseFailure(int errorCode);
    }

    public static final int STATE_READY       = 1;
    public static final int STATE_STARTING    = 2;
    public static final int STATE_ADVERTISING = 3;

    private Context context;
    private Listener listener;
    private BluetoothManager manager;
    private BluetoothAdapter adapter;
    private BluetoothLeAdvertiser advertiser;

    private int advertiseMode = AdvertiseSettings.ADVERTISE_MODE_BALANCED;
    private int advertiseTxPower = AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM;

    private int state = STATE_READY;

    private AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            if (state == STATE_STARTING) {
                state = STATE_ADVERTISING;
                if (listener != null) {
                    listener.onAdvertiseSuccess(settingsInEffect);
                }
            }

        }
        @Override
        public void onStartFailure(int errorCode) {
            if (state == STATE_STARTING) {
                if (listener != null) {
                    listener.onAdvertiseFailure(errorCode);
                }
            }
            state = STATE_READY;
        }
    };


    public Advertiser(Context context) {
        this.context = context;
        this.manager =
                (BluetoothManager)context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (this.manager != null) {
            this.adapter = manager.getAdapter();
        }
    }

    public void setAdvertiseMode(int mode) {
        this.advertiseMode = mode;
    }

    public void setAdvertiseTxPower(int power) {
        this.advertiseTxPower = power;
    }

    public boolean systemSupported() {
        if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            return false;
        }

        if (manager == null) {
            return false;
        }
        if (adapter == null) {
            return false;
        }

        if (!adapter.isMultipleAdvertisementSupported()) {
            return false;
        }
        return true;
    }

    public boolean isEnabled() {
        return (adapter != null && adapter.isEnabled());
    }

    private AdvertiseSettings createAdvertiseSettings() {
        AdvertiseSettings.Builder builder = new AdvertiseSettings.Builder();
        builder.setAdvertiseMode(this.advertiseMode);
        builder.setTxPowerLevel(this.advertiseTxPower);
        builder.setConnectable(false);

        return builder.build();
    }

    public void start(String url) {
        start(url, null);
    }

    public boolean isReady() {
        return (state == STATE_READY);
    }

    public boolean isAdvertising() {
        return (state == STATE_ADVERTISING);
    }

    public boolean start(String url, Listener listener) {
        if (state != STATE_READY) {
            return false;
        }
        advertiser = adapter.getBluetoothLeAdvertiser();
        if (advertiser == null) {
            return false;
        }
        this.listener = listener;
        AdvertiseSettings setting = createAdvertiseSettings();
        AdvertiseData data = URLFrame.createAdvertiseData(url);
        this.state = STATE_STARTING;
        advertiser.startAdvertising(setting, data, advertiseCallback);
        return true;
    }

    public boolean stop() {
        if (state != STATE_ADVERTISING) {
            return false;
        }
        if (advertiser != null) {
            advertiser.stopAdvertising(this.advertiseCallback);
        }
        state = STATE_READY;
        return true;
    }



}
