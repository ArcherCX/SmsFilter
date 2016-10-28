package com.example.archer.smsfilter.util;

import android.os.Build;
import android.os.Message;
import android.telephony.SmsMessage;

import com.example.archer.smsfilter.service.SmsFilterService;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

/**
 * Description:
 *
 * @author Chen Xin
 * @version 1.0
 * @since 15/12/30
 */
public class XSmsFilter implements IXposedHookZygoteInit {
    private final String TAG = "XSmsFilter";

    /*
     * ActivityManagerService is the beginning of the main "android"
     * process. This is where the core java system is started, where the
     * system context is created and so on. In pre-lollipop we can access
     * this class directly, but in lollipop we have to visit ActivityThread
     * first, since this class is now responsible for creating a class
     * loader that can be used to access ActivityManagerService. It is no
     * longer possible to do so via the normal boot class loader. Doing it
     * like this will create a consistency between older and newer Android
     * versions.
     *
     * Note that there is no need to handle arguments in this case. And we
     * don't need them so in case they change over time, we will simply use
     * the hookAll feature.
     *
     * Wait till startBootstrapServices start PackageManagerService , then
     * get PackageManager to get all app who try to receive sms.
     */
    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        try {
            registerBinder();
            Class<?> cInboundSmsHandler = Class.forName("com.android.internal.telephony.InboundSmsHandler$DeliveringState");
            findAndHookMethod(cInboundSmsHandler, "processMessage", Message.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        Message rilMsg = (Message) param.args[0];
                        XposedBridge.log("Sms what = " + rilMsg.what);
                        /*com.android.internal.telephony.InboundSmsHandler.EVENT_NEW_SMS = 1;
                        * com.android.internal.telephony.InboundSmsHandler.EVENT_INJECT_SMS = 8;*/
                        if (rilMsg.what == 1 || rilMsg.what == 8) {
                            Class<?> AsyncResult = Class.forName("android.os.AsyncResult");
                            Field result = AsyncResult.getField("result");
                            SmsMessage sms = (SmsMessage) result.get(rilMsg.obj);
                            if (sms != null) {
                                Field mWrappedSmsMessage = SmsMessage.class.getDeclaredField("mWrappedSmsMessage");
                                mWrappedSmsMessage.setAccessible(true);
                                Class<?> SmsMessageBase = Class.forName("com.android.internal.telephony.SmsMessageBase");
                                Field mMessageBody = SmsMessageBase.getDeclaredField("mMessageBody");
                                mMessageBody.setAccessible(true);
                                String msgBody = (String) mMessageBody.get(mWrappedSmsMessage.get(sms));
                                ISmsFilterService client = SmsFilterService.getClient();
                                String filterKeyword = client.getFilterKeyword();
                                boolean matchRs = msgBody.matches(".*(" + filterKeyword + ").*");
                                XposedBridge.log("client.getFilterKeyword() : " + filterKeyword + " -- and match result : " + matchRs);
                                if (matchRs) {
                                    ((Message) param.args[0]).what = Integer.MAX_VALUE;
                                }
                            }
                        }
                    } catch (ClassNotFoundException e) {
                        XposedBridge.log(e);
                    }
                }
            });
        } catch (Throwable e) {
            XposedBridge.log("initZygote error:");
            XposedBridge.log(e);
        }
    }


    /**
     * 注册IBinder服务到ServiceManager中
     *
     * @throws ClassNotFoundException
     * @throws NoSuchMethodException
     */
    private void registerBinder() throws ClassNotFoundException, NoSuchMethodException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Class<?> at = Class.forName("android.app.ActivityThread");
            XposedBridge.hookAllMethods(at, "systemMain", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        final ClassLoader loader = Thread.currentThread().getContextClassLoader();
                        Class<?> am = Class.forName("com.android.server.am.ActivityManagerService", false, loader);
                        XposedBridge.hookAllConstructors(am, new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                try {
                                    SmsFilterService.register(loader);
                                } catch (Throwable ex) {
                                    ex.printStackTrace();
                                    Log.e(TAG, ex.getMessage());
                                }
                            }
                        });
                    } catch (Throwable ex) {
                        ex.printStackTrace();
                        Log.e(TAG, ex.getMessage());
                    }
                }
            });

        } else {
            Class<?> cSystemServer = Class.forName("com.android.server.SystemServer");
            Method mMain = cSystemServer.getDeclaredMethod("main", String[].class);
            XposedBridge.hookMethod(mMain, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        SmsFilterService.register(null);
                    } catch (Throwable ex) {
                        ex.printStackTrace();
                        Log.e(TAG, ex.getMessage());
                    }
                }
            });
        }
    }
}
