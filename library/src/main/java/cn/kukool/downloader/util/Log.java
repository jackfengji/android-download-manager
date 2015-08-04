package cn.kukool.downloader.util;

import cn.kukool.downloader.BuildConfig;

/**
 * User: jackfengji
 * Date: 15/6/25
 */
public class Log {
    private static final String TAG = "Log";

    public static void error(String tag, String msg) {
        android.util.Log.e(tag, msg);
    }

    public static void error(String tag, String msg, Throwable e) {
        android.util.Log.e(tag, msg, e);
    }

    public static void info(String tag, String msg) {
        android.util.Log.i(tag, msg);
    }

    public static void debug(String tag, String msg) {
        if (BuildConfig.DEBUG) {
            android.util.Log.d(tag, msg);
        }
    }

    public static void assertLog(boolean result, String tag, String msg) {
        if (!result) {
            error(tag, msg, new RuntimeException("Assert failed"));
        }
    }
}
