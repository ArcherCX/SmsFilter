package com.example.archer.smsfilter.service;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.os.RemoteException;

import com.example.archer.smsfilter.util.ISmsFilterService;
import com.example.archer.smsfilter.util.Log;

/**
 * 处理进程间通信服务
 */
public class SmsFilterService extends Service {
    private static final String SHARED_NAME = "sms_filter";
    private final String TAG = "SmsFilterService";

    private SharedPreferences sp;

    public SmsFilterService() {
    }

    @Override
    public void onCreate() {
        Log.e(TAG, "SmsFilterService onCreat");
        sp = getSharedPreferences(SHARED_NAME, MODE_PRIVATE);
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.e(TAG, "client want bind!!!!");
        return myBinder;
    }

    private final IBinder myBinder = new ISmsFilterService.Stub() {

        @Override
        public String getFilterKeyword() throws RemoteException {
            return sp.getString("test", "testBinder");
        }
    };
}
