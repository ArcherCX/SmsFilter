package com.example.archer.smsfilter;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;

/**
 * Description:
 *
 * @author Chen Xin
 * @version 1.0
 * @since 15/12/30
 */
public class TestPackageLoad implements IXposedHookLoadPackage {
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
        Log.e("Xposed", packageName+" PackageLoaded now !!!!!");
        XC_MethodHook xc_methodHookR = new MyXC_MethodHook(targetClassReceiver);
        XC_MethodHook xc_methodHookD = new MyXC_MethodHook(targetClassDeliver);
        findAndHookMethod(targetClassReceiver, lpparam.classLoader, hookMethod, Context.class, Intent.class, xc_methodHookR);
        findAndHookMethod(targetClassDeliver, lpparam.classLoader, hookMethod, Context.class, Intent.class, xc_methodHookD);
    }

    class MyXC_MethodHook extends XC_MethodHook {

        private String hookClass;

        MyXC_MethodHook(String hookClass) {
            this.hookClass = hookClass;
        }

        @Override
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            XposedBridge.log(hookClass+"'s has been hooked!!!!!");
            param.setResult(null);
        }
    }
}
