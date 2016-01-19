package com.example.archer.smsfilter;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.provider.Telephony;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

/**
 * Description:
 *
 * @author Chen Xin
 * @version 1.0
 * @since 15/12/30
 */
public class XSmsFilter implements IXposedHookLoadPackage, IXposedHookZygoteInit {
    private final String TAG = "XSmsFilter";
    private final String hangoutsPkgName = "com.google.android.talk";
    //    private final String hangoutsPkgName = "com.example.archer.simplestapp";
    private final String targetClassReceiver = "com.google.android.apps.hangouts.sms.SmsReceiver";
    private final String targetClassDeliver = "com.google.android.apps.hangouts.sms.SmsDeliverReceiver";
    //    private final String targetClass = "com.example.archer.simplestapp.MyReceiver";
    private final String hookMethod = "onReceive";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        String packageName = lpparam.packageName;
        if (!packageName.equals(hangoutsPkgName)) {
            return;
        }
//        XposedBridge.hookAllMethods();
        Log.e("Xposed", packageName + " PackageLoaded now !!!!!");
        XC_MethodHook xc_methodHookR = new MyXC_MethodHook(targetClassReceiver);
        XC_MethodHook xc_methodHookD = new MyXC_MethodHook(targetClassDeliver);
        findAndHookMethod(targetClassReceiver, lpparam.classLoader, hookMethod, Context.class, Intent.class, xc_methodHookR);
        findAndHookMethod(targetClassDeliver, lpparam.classLoader, hookMethod, Context.class, Intent.class, xc_methodHookD);
    }

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
     */
    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Class<?> at = Class.forName("android.app.ActivityThread");
                XposedBridge.hookAllMethods(at, "systemMain", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            final ClassLoader loader = Thread.currentThread().getContextClassLoader();
                            final Class<?> am = Class.forName("com.android.server.am.ActivityManagerService", false, loader);
                            XposedBridge.hookAllConstructors(am, new XC_MethodHook() {
                                @Override
                                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                    try {
                                        Class<?> ssClass = Class.forName("com.android.server.SystemServer", false, loader);

                                        Field fContext = am.getDeclaredField("mContext");
                                        fContext.setAccessible(true);
                                        Context context = (Context) fContext.get(param.thisObject);
                                        PackageManager pm = null;
                                        if (context == null) {
                                            Log.e(TAG, "context is null");
                                        } else {
                                            pm = context.getPackageManager();
                                            if (pm == null) {
                                                Log.e(TAG, "pm is null");
                                            }
                                        }
//                                        Method mGetPackageManager = Context.class.getDeclaredMethod("getPackageManager");
//                                        PackageManager pm = (PackageManager) mGetPackageManager.invoke(context);
//                                        if (pm == null) {
//                                            Log.e(TAG, "pm is null");
//                                        }
                                        Intent intentD = new Intent();
                                        intentD.setAction(Telephony.Sms.Intents.SMS_DELIVER_ACTION);
                                        Intent intentR = new Intent();
                                        intentR.setAction(Telephony.Sms.Intents.SMS_RECEIVED_ACTION);
                                        List<ResolveInfo> deliverSms = pm.queryBroadcastReceivers(intentD, 0);
                                        for (ResolveInfo info :
                                                deliverSms) {
                                            String packageName = info.activityInfo.packageName;
                                            Log.d(TAG, "packageNameD : " + packageName + "---" + info.activityInfo.name);
                                        }
                                        List<ResolveInfo> receiverSms = pm.queryBroadcastReceivers(intentR, PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER);
                                        for (ResolveInfo info :
                                                receiverSms) {
                                            String packageName = info.activityInfo.packageName;
                                            Log.i(TAG, "packageNameR : " + packageName + "---" + info.activityInfo.name);
                                        }
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
                final Class<?> cSystemServer = Class.forName("com.android.server.SystemServer");
                Method mMain = cSystemServer.getDeclaredMethod("main", String[].class);
                XposedBridge.hookMethod(mMain, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            Field fContext = cSystemServer.getDeclaredField("mSystemContext");
                            fContext.setAccessible(true);
                            Method mGetPackageManager = Context.class.getDeclaredMethod("getPackageManager");
                            PackageManager pm = (PackageManager) mGetPackageManager.invoke(fContext);
                            Intent intentD = new Intent();
                            intentD.setAction(Telephony.Sms.Intents.SMS_DELIVER_ACTION);
                            Intent intentR = new Intent();
                            intentR.setAction(Telephony.Sms.Intents.SMS_RECEIVED_ACTION);
                            List<ResolveInfo> deliverSms = pm.queryBroadcastReceivers(intentD, 0);
                            for (ResolveInfo info :
                                    deliverSms) {
                                String packageName = info.activityInfo.packageName;
                                Log.d(TAG, "packageNameD : " + packageName + "---" + info.activityInfo.name);
                            }
                            List<ResolveInfo> receiverSms = pm.queryBroadcastReceivers(intentR, PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER);
                            for (ResolveInfo info :
                                    receiverSms) {
                                String packageName = info.activityInfo.packageName;
                                Log.i(TAG, "packageNameR : " + packageName + "---" + info.activityInfo.name);
                            }
                        } catch (Throwable ex) {
                            ex.printStackTrace();
                            Log.e(TAG, ex.getMessage());
                        }
                    }
                });
            }
        } catch (Throwable ex) {
            ex.printStackTrace();
            Log.e(TAG, ex.getMessage());
        }
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
