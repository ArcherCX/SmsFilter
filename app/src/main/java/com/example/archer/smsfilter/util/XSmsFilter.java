package com.example.archer.smsfilter.util;

import android.app.AndroidAppHelper;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.IBinder;
import android.os.Message;
import android.os.Process;
import android.os.RemoteException;
import android.provider.Telephony;
import android.telephony.SmsMessage;

import java.lang.reflect.Field;
import java.util.List;

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
                                Log.i(TAG, Process.myPid() + "---" + Process.myUid() + "---Message Body here:" + msgBody);
                                bindService(AndroidAppHelper.currentApplication());
                            }
                        }
                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                        Log.e(TAG, "hook processMessage error: " + e.getMessage() + "--- detail:" + e.toString() + "\n");
                    }
                }
            });
        } catch (Throwable e) {
            e.printStackTrace();
            Log.e(TAG, "initZygote error: " + e.getMessage() + "--- detail:" + e.toString() + "\n");
            StackTraceElement[] stackTrace = e.getStackTrace();
            for (StackTraceElement element : stackTrace) {
                Log.e(TAG, element.toString());
            }
        }

        /*try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Class<?> at = Class.forName("android.app.ActivityThread");
                XposedBridge.hookAllMethods(at, "systemMain", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            final ClassLoader loader = Thread.currentThread().getContextClassLoader();
                            final Class<?> cSystemServer = Class.forName("com.android.server.SystemServer", false, loader);
                            getSmsReceiver(cSystemServer);
                        } catch (Throwable ex) {
                            ex.printStackTrace();
                            Log.e(TAG, ex.getMessage());
                        }
                    }

                });

            } else {
                final Class<?> cSystemServer = Class.forName("com.android.server.SystemServer");
                getSmsReceiver(cSystemServer);
            }
        } catch (Throwable ex) {
            ex.printStackTrace();
            Log.e(TAG, ex.getMessage());
        }*/
    }

    private void bindService(final Context mContext) {
        try {
            Intent service = new Intent("com.example.archer.smsfilter.service.SmsFilterService");
            service.setPackage("com.example.archer.smsfilter");
            boolean b = mContext.bindService(service, new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    ISmsFilterService server = ISmsFilterService.Stub.asInterface(service);
                    try {
                        Log.i(TAG, "server.getFilterKeyword():" + server.getFilterKeyword());
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    } finally {
                        mContext.unbindService(this);
                    }
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    android.util.Log.e(TAG, "service unbind");
                }
            }, Context.BIND_AUTO_CREATE);
            android.util.Log.e(TAG, "bind Result:" + b);
        } catch (Throwable e) {
            e.printStackTrace();
            Log.e(TAG, "bindService error: " + e.getMessage() + "--- detail:" + e.toString() + "\n");
            StackTraceElement[] stackTrace = e.getStackTrace();
            for (StackTraceElement element : stackTrace) {
                Log.e(TAG, element.toString());
            }
        }
    }

    private void getSmsReceiver(final Class<?> ssClass) {
        findAndHookMethod(ssClass, "startBootstrapServices", new XC_MethodHook() {

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Field fContext = ssClass.getDeclaredField("mSystemContext");
                fContext.setAccessible(true);
                Context context = (Context) fContext.get(param.thisObject);
                PackageManager pm = context.getPackageManager();
                Intent intentD = new Intent();
                intentD.setAction(Telephony.Sms.Intents.SMS_DELIVER_ACTION);
                Intent intentR = new Intent();
                intentR.setAction(Telephony.Sms.Intents.SMS_RECEIVED_ACTION);
                List<ResolveInfo> deliverSms = pm.queryBroadcastReceivers(intentD, 0);

                for (ResolveInfo info : deliverSms) {
                    String packageName = info.activityInfo.packageName;
                    Log.d(TAG, " --- packageNameD : " + packageName + "---" + info.activityInfo.name);
                }
                deliverSms.addAll(pm.queryBroadcastReceivers(intentR, PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER));
                for (ResolveInfo info : deliverSms) {
                    String packageName = info.activityInfo.packageName;
                    Log.i(TAG, "packageNameR&D : " + packageName + "---" + info.activityInfo.name);
                }
            }
        });
    }

    class MyXC_MethodHook extends XC_MethodHook {

        private String hookClass;

        MyXC_MethodHook(String hookClass) {
            this.hookClass = hookClass;
        }

        @Override
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            XposedBridge.log(hookClass + "'s has been hooked!!!!!");
//            param.setResult(null);
        }
    }
}
