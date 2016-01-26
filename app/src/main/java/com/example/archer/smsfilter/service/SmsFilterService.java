package com.example.archer.smsfilter.service;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.IBinder;
import android.os.RemoteException;

import com.example.archer.smsfilter.util.ISmsFilterService;
import com.example.archer.smsfilter.util.Log;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * 处理进程间通信服务
 */
public class SmsFilterService extends ISmsFilterService.Stub {
    private static ISmsFilterService mClient;
    private static final String TAG = "SmsFilterService";
    private final static String cServiceName = "xsmsfilter";
    private final String KEY_WORD_TABLE = "sms_filter_keyword";
    private SQLiteDatabase sqLiteDatabase;
    private final String dbPath = "/data/system/smsfilter/sms_filter.db";

    private SmsFilterService() {
    }

    /**
     * 注册服务端
     *
     * @param classLoader 类加载器
     */
    public static void register(ClassLoader classLoader) {
        try {
            SmsFilterService mSmsFilterService = new SmsFilterService();
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
        SQLiteDatabase db = getDb();
        StringBuilder sb = new StringBuilder();
        Cursor query = db.query(KEY_WORD_TABLE, new String[]{"keyword"}, null, null, null, null, null);
        if (query != null) {
            while (query.moveToNext()) {
                sb.append(query.getString(0) + "|");
            }
            query.close();
        }
        if (sb.toString().endsWith("|")) {
            int end = sb.length();
            sb.delete(end - 1, end);
        }
        return sb.toString();
    }

    private SQLiteDatabase getDb() {
        if (sqLiteDatabase != null && !sqLiteDatabase.isOpen()) {
            sqLiteDatabase = null;
        }
        File dbFile = new File(dbPath);
        if (sqLiteDatabase == null) {
            File parentFile = dbFile.getParentFile();
            if (!parentFile.exists()) {
                boolean mkdirs = parentFile.mkdirs();
                if (!mkdirs) {
                    Log.e(TAG, dbFile.getAbsolutePath() + " 's parent create failed!!!!");
                }
            }
            sqLiteDatabase = SQLiteDatabase.openOrCreateDatabase(dbFile, null);
        }
        if (!sqLiteDatabase.isDatabaseIntegrityOk()) {
            sqLiteDatabase.close();
            dbFile.renameTo(new File(dbFile.getParentFile(), "sms_filter-backup"));
            File journalFile = new File(dbFile.getAbsolutePath() + "-journal");
            journalFile.renameTo(new File(dbFile.getAbsoluteFile(), "sms_filter-backup-journal"));
            sqLiteDatabase = SQLiteDatabase.openOrCreateDatabase(dbFile, null);
        }
        if (sqLiteDatabase.needUpgrade(1)) {
            try {
                sqLiteDatabase.beginTransaction();
                sqLiteDatabase.execSQL("create table " + KEY_WORD_TABLE + " (_id integer primary key autoincrement not null, keyword text not null)");
                ContentValues contentValues = new ContentValues();
                contentValues.put("keyword", "Test Intent");
                sqLiteDatabase.insert(KEY_WORD_TABLE, null, contentValues);
                sqLiteDatabase.setVersion(1);
                sqLiteDatabase.setTransactionSuccessful();
            } finally {
                sqLiteDatabase.endTransaction();
            }
        }
        return sqLiteDatabase;
    }
}
