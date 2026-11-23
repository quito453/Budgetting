package com.valiugt.app;

import android.content.Intent;
import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(ValiuPlugin.class);   // Android-only features (widgets, automatic capture)
        super.onCreate(savedInstanceState);
        ValiuPlugin.takeIntent(getIntent());
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        ValiuPlugin.takeIntent(intent);
    }
}
