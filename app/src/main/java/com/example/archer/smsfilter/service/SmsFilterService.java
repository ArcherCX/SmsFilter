package com.example.archer.smsfilter.service;

import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.os.RemoteException;

import com.example.archer.smsfilter.util.ISmsFilterService;
import com.example.archer.smsfilter.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * 处理进程间通信服务
 */
public class SmsFilterService extends ISmsFilterService.Stub {
    private static ISmsFilterService mClient;
    private static final String TAG = "SmsFilterService";
    private final static String cServiceName = "xsmsfilter";

    private SharedPreferences sp;
    private static SmsFilterService mSmsFilterService;

    private SmsFilterService() {
    }

    /**
     * 注册服务端
     *
     * @param classLoader 类加载器
     */
    public static void register(ClassLoader classLoader) {
        try {
            mSmsFilterService = new SmsFilterService();
            Class<?> cServiceManager = Class.forName("android.os.ServiceManager", false, classLoader);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                Method mAddService = cServiceManager.getDeclaredMethod("addService", String.class, IBinder.class,
                        boolean.class);
                mAddService.invoke(null, getServiceName(), mSmsFilterService, true);
            } else {
                Method mAddService = cServiceManager.getDeclaredMethod("addService", String.class, IBinder.class);
                mAddService.invoke(null, getServiceName(), mSmsFilterService);
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取客户端
     *
     * @return 客户端
     */
    public static ISmsFilterService getClient() {
        if (mClient == null)
            try {
                // public static IBinder getService(String name)
                Class<?> cServiceManager = Class.forName("android.os.ServiceManager");
                Method mGetService = cServiceManager.getDeclaredMethod("getService", String.class);
                mClient = ISmsFilterService.Stub.asInterface((IBinder) mGetService.invoke(null, getServiceName()));
            } catch (Throwable ex) {
                ex.printStackTrace();
                Log.e(TAG, ex.getMessage());
            }

        return mClient;
    }

    private static String getServiceName() {
        return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ? "user." : "") + cServiceName;
    }

    @Override
    public String getFilterKeyword() throws RemoteException {
        return "getFilterKeyword through ServiceManager !!!!!";
    }
}
