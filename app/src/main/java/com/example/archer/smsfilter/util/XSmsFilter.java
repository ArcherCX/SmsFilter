package com.example.archer.smsfilter.util;

import android.os.Build;
import android.os.Message;
import android.os.Parcel;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.SparseArray;

import com.example.archer.smsfilter.service.SmsFilterService;

import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.findMethodExact;
import static de.robv.android.xposed.XposedHelpers.getIntField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.getStaticIntField;

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
            final Class<?> cInboundSmsHandler = findClass("com.android.internal.telephony.InboundSmsHandler", null);
//            final Class<?> cHookState = Class.forName("com.android.internal.telephony.InboundSmsHandler$DeliveringState");
//            final Class<?> cHookState = Class.forName("com.android.internal.telephony.InboundSmsHandler$IdleState");
            final Class<?> cHookState = Class.forName("com.android.internal.util.StateMachine$SmHandler");
//            findAndHookMethod(cHookState, "processMessage", Message.class, new XC_MethodHook() {
            findAndHookMethod(cHookState, "handleMessage", Message.class, new XC_MethodHook() {

                private boolean matchRs;

                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        Message rilMsg = (Message) param.args[0];
//                        XposedBridge.log("hook method : DeliveringState.processMessage --- Sms what = " + rilMsg.what);
                        if (rilMsg.what < 10) {
                            XposedBridge.log("hook method : SmHandler.handleMessage --- Sms what = " + rilMsg.what);
                        }
                        /*com.android.internal.telephony.InboundSmsHandler.EVENT_NEW_SMS = 1;
                        * com.android.internal.telephony.InboundSmsHandler.EVENT_INJECT_SMS = 8;*/
                        int EVENT_NEW_SMS = getStaticIntField(cInboundSmsHandler, "EVENT_NEW_SMS");
                        if (rilMsg.what == EVENT_NEW_SMS) {
                            Object msgObj = getObjectField(rilMsg.obj, "result");
                            if (msgObj != null && msgObj.getClass() == SmsMessage.class) {
                                SmsMessage sms = (SmsMessage) msgObj;
                                String msgBody = (String) getObjectField(getObjectField(sms, "mWrappedSmsMessage"), "mMessageBody");
                                ISmsFilterService client = SmsFilterService.getClient();
                                String filterKeyword = client.getFilterKeyword();
                                matchRs = msgBody.matches(".*(" + filterKeyword + ").*");
                                if (matchRs) {
                                    //取消对该消息的处理（handleMessage的调用）
                                    param.setResult(null);
                                    //通知RIL该条短信已经被处理，防止RIL block不再接收新消息
                                    int mStateStackTopIndex = getIntField(param.thisObject, "mStateStackTopIndex");
                                    Object[] mStateStack = (Object[]) getObjectField(param.thisObject, "mStateStack");
                                    Object state = getObjectField(mStateStack[mStateStackTopIndex], "state");
                                    Object outerInboundSmsHandler = getObjectField(state, "this$0");
                                    Method acknowledgeLastIncomingSms = findMethodExact(outerInboundSmsHandler.getClass(), "acknowledgeLastIncomingSms", boolean.class, int.class, Message.class);
                                    acknowledgeLastIncomingSms.invoke(outerInboundSmsHandler, true, Telephony.Sms.Intents.RESULT_SMS_HANDLED, null);
                                }
//                                XposedBridge.log("hook method : DeliveringState.processMessage --- client.getFilterKeyword() : " + filterKeyword + " -- and match result : " + matchRs + " --- msg = " + msgBody);
                                XposedBridge.log("hook method : IdleState.processMessage --- client.getFilterKeyword() : " + filterKeyword + " -- and match result : " + matchRs + " --- msg = " + msgBody);
                            }
                        }
                    } catch (XposedHelpers.ClassNotFoundError e) {
                        XposedBridge.log(e);
                    }
                }
            });
        } catch (Throwable e) {
            XposedBridge.log("initZygote error:");
            XposedBridge.log(e);
        }
//        debugHook();
    }

    private void debugHook() {
        Class<?> cHook = null;

        try {
            cHook = Class.forName("android.telephony.SmsMessage");
        } catch (ClassNotFoundException e) {
            XposedBridge.log(e);
        }
        if (cHook != null) {
            findAndHookMethod(cHook, "newFromCMT", String[].class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    String[] msg = (String[]) param.args[0];
                    for (int i = 0; i < msg.length; i++) {
                        XposedBridge.log("newFromCMT line[" + i + "] = " + msg[i]);
                    }
                }
            });
            findAndHookMethod(cHook, "newFromParcel", Parcel.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    SmsMessage result = (SmsMessage) param.getResult();
                    Object mWrappedSmsMessage = getObjectField(result, "mWrappedSmsMessage");
                    String mMessageBody = (String) getObjectField(mWrappedSmsMessage, "mMessageBody");
                    XposedBridge.log("newFromParcel message body = " + mMessageBody);
                }
            });

            try {
                cHook = findClass("com.android.internal.util.State", null);
                findAndHookMethod(cHook, "enter", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.thisObject.getClass().getCanonicalName().startsWith("com.android.internal.telephony.InboundSmsHandler")) {
                            XposedBridge.log(param.thisObject.getClass().getName() + " !!! Enter");
                        }
                    }
                });
                findAndHookMethod(cHook, "exit", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.thisObject.getClass().getCanonicalName().startsWith("com.android.internal.telephony.InboundSmsHandler")) {
                            XposedBridge.log(param.thisObject.getClass().getName() + " !!! Exit");
                        }
                    }
                });
                findAndHookMethod(cHook, "processMessage", Message.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Message msg = (Message) param.args[0];
                        if (param.thisObject.getClass().getCanonicalName().startsWith("com.android.internal.telephony.InboundSmsHandler")) {
                            XposedBridge.log(param.thisObject.getClass().getName() + " !!! processMessage , and what = " + msg.what);
                        }
                    }
                });
            } catch (XposedHelpers.ClassNotFoundError e) {
                XposedBridge.log(e);
            }

            try {
                cHook = findClass("com.android.internal.util.StateMachine$SmHandler", null);
                Class<?> cState = findClass("com.android.internal.util.State", null);
                findAndHookMethod(cHook, "performTransitions", cState, Message.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.args[0] == null || !param.args[0].getClass().getCanonicalName().startsWith("com.android.internal.telephony.InboundSmsHandler"))
                            return;
                        String paramStateClass = param.args[0].getClass().getSimpleName();
                        Object mLogRecords = getObjectField(param.thisObject, "mLogRecords");
                        Method mLogOnlyTransitions = findMethodExact(mLogRecords.getClass(), "logOnlyTransitions");
                        boolean LogOnlyTransitions = (boolean) mLogOnlyTransitions.invoke(mLogRecords);
                        Object[] mStateStack = (Object[]) getObjectField(param.thisObject, "mStateStack");
                        int mStateStackTopIndex = getIntField(param.thisObject, "mStateStackTopIndex");
                        Object orgState = getObjectField(mStateStack[mStateStackTopIndex], "state");
                        Object mDestState = getObjectField(param.thisObject, "mDestState");
                        XposedBridge.log("performTransitions msgProcessedState is : " + paramStateClass + " -- logOnlyTransitions = " + LogOnlyTransitions + " -- orgState : " + orgState.getClass().getSimpleName() + " -- mDestState is " + ((mDestState == null) ? "null" : mDestState.getClass().getSimpleName()));
                    }
                });
                Class<?> cStateInfo = findClass("com.android.internal.util.StateMachine$SmHandler$StateInfo", null);
                findAndHookMethod(cHook, "invokeExitMethods", cStateInfo, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
//                        (mStateStackTopIndex >= 0) && (mStateStack[mStateStackTopIndex] != commonStateInfo)
                        boolean b = param.args[0] != null;
                        int mStateStackTopIndex = getIntField(param.thisObject, "mStateStackTopIndex");
                        Log.d(TAG, (b ? "invokeExitMethods param type is : " + getObjectField(param.args[0], "state").getClass().getSimpleName() : "invokeExitMethods param is null")
                                + " -- and mStateStackTopIndex = " + mStateStackTopIndex
                                + " -- mStateStack[mStateStackTopIndex] != commonStateInfo ? " + getObjectField(((Object[]) getObjectField(param.thisObject, "mStateStack"))[mStateStackTopIndex], "state").getClass().getSimpleName());
                    }
                });
                findAndHookMethod(cHook, "invokeEnterMethods", int.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        android.util.Log.w(TAG, "invokeEnterMethods: stateStackEnteringIndex = " + param.args[0] + " -- mStateStackTopIndex = " + getIntField(param.thisObject, "mStateStackTopIndex"));
                    }
                });
            } catch (XposedHelpers.ClassNotFoundError e) {
                XposedBridge.log(e);
            }

            try {
                cHook = findClass("com.android.internal.telephony.RIL", null);
                final Class<?> finalCHook = cHook;
                findAndHookMethod(cHook, "processUnsolicited", Parcel.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Parcel p = (Parcel) param.args[0];
                        int curPos = p.dataPosition();
                        int response = p.readInt();
                        Method responseToString = findMethodExact(finalCHook, "responseToString", int.class);
                        String responsString = (String) responseToString.invoke(null, response);
//                        if (responsString.toLowerCase().contains("sms") || responsString.equals("UNSOL_RESPONSE_RADIO_STATE_CHANGED")) {
//                        }
                        android.util.Log.e(TAG, "RIL processUnsolicited response : " + responsString + " -- " + response);
                        p.setDataPosition(curPos);
                    }
                });
                findAndHookMethod(cHook, "processSolicited", Parcel.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Parcel p = (Parcel) param.args[0];
                        int curPos = p.dataPosition();
                        int serial = p.readInt();
                        SparseArray mRequestList = (SparseArray) getObjectField(param.thisObject, "mRequestList");
                        Object rilRequest = mRequestList.get(serial);
                        if (rilRequest == null) {
                            Log.d(TAG, "processSolicited: doesn't get serial " + serial + " request,it is null");
                        } else {
                            int mRequest = getIntField(rilRequest, "mRequest");
                            Method requestToString = findMethodExact(finalCHook, "requestToString", int.class);
                            String requestString = (String) requestToString.invoke(null, mRequest);
                            android.util.Log.v(TAG, "RIL processSolicited request : " + requestString + " -- " + serial);
                        }
                        p.setDataPosition(curPos);
                    }
                });
            } catch (XposedHelpers.ClassNotFoundError e) {
                XposedBridge.log(e);
            }
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
