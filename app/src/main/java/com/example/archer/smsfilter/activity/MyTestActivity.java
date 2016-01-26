package com.example.archer.smsfilter.activity;

import android.os.Bundle;
import android.os.Process;
import android.os.RemoteException;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;

import com.example.archer.smsfilter.R;
import com.example.archer.smsfilter.service.SmsFilterService;
import com.example.archer.smsfilter.util.Log;

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
                try {
                    Log.d(TAG, Process.myPid() + "---" + Process.getUidForName("system") + "---" + SmsFilterService.getClient().getFilterKeyword());
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        });
    }

}
