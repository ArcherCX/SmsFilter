package com.example.archer.smsfilter.activity;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.provider.Telephony;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.example.archer.smsfilter.R;

import java.util.List;

public class MyTestActivity extends AppCompatActivity {
    private final String Tag = "MyTestActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_test);
        Button btn = (Button) findViewById(R.id.test_btn);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intentD = new Intent();
                intentD.setAction(Telephony.Sms.Intents.SMS_DELIVER_ACTION);
                Intent intentR = new Intent();
                intentR.setAction(Telephony.Sms.Intents.SMS_RECEIVED_ACTION);
                PackageManager packageManager = getPackageManager();
                List<ResolveInfo> deliverSms = packageManager.queryBroadcastReceivers(intentD, 0);
                for (ResolveInfo info :
                        deliverSms) {
                    String packageName = info.activityInfo.packageName;
                    Log.d(Tag, "packageNameD : " + packageName + "---" + info.activityInfo.name);
                }
                List<ResolveInfo> receiverSms = packageManager.queryBroadcastReceivers(intentR, PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER);
                for (ResolveInfo info :
                        receiverSms) {
                    String packageName = info.activityInfo.packageName;
                    Log.i(Tag, "packageNameR : " + packageName + "---" + info.activityInfo.name);
                }
            }
        });
    }
}
