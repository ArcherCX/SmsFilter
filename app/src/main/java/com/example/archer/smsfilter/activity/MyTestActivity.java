package com.example.archer.smsfilter.activity;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;

import com.example.archer.smsfilter.R;
import com.example.archer.smsfilter.util.ISmsFilterService;
import com.example.archer.smsfilter.util.Log;

import java.util.List;

public class MyTestActivity extends AppCompatActivity {
    private final String TAG = "MyTestActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_test);
        Button btn = (Button) findViewById(R.id.test_btn);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Intent service = new Intent("com.example.archer.smsfilter.service.SmsFilterService");
                List<ResolveInfo> resolveInfos = getPackageManager().queryIntentServices(service, 0);
                try {
                    Class<?> serC = Class.forName(resolveInfos.get(0).serviceInfo.name);
                    service = new Intent(getApplicationContext(), serC);
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
                boolean b = bindService(service, new ServiceConnection() {
                    @Override
                    public void onServiceConnected(ComponentName name, IBinder service) {
                        ISmsFilterService server = (ISmsFilterService) service;
                        try {
                            Log.i(TAG, "server.getFilterKeyword():" + server.getFilterKeyword());
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        } finally {
                            unbindService(this);
                        }
                    }

                    @Override
                    public void onServiceDisconnected(ComponentName name) {
                        Log.e(TAG, "service unbind");
                    }
                }, BIND_AUTO_CREATE);
                Log.e(TAG, "bind Result:" + b);
            }
        });
    }
}
