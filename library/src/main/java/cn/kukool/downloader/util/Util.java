package cn.kukool.downloader.util;

import android.os.Environment;

import java.io.File;

public class Util {
    public static boolean isSdcardOK() {
        try {
            return Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public static String getSdcardPath() {
        return Environment.getExternalStorageDirectory().getAbsolutePath();
    }

    public static boolean checkSdcard() {
        File sdcard = new File(Util.getSdcardPath());
        return !(!sdcard.exists() || !sdcard.canWrite());
    }

    public static final String strNum = "0123456789abcdefABCDEF";

    public static boolean isGB2312(String str) {
        int count = 0;
        char apBuf[] = str.toCharArray();
        for (int i = 0; i < apBuf.length; i++) {
            char chr = apBuf[i];
            if (chr == '%') {
                count++;
                if (i + 2 > apBuf.length - 1) {
                    return false;
                }
                String snum = str.substring(i + 1, i + 3);
                if (strNum.indexOf(snum.charAt(0)) == -1
                        || strNum.indexOf(snum.charAt(1)) == -1) {
                    return false;
                }
                int n = Integer.parseInt(snum, 16);
                if (n > 127) {
                    if ((n >= 228) && (n <= 233)) {
                        if ((i + 2 + 6) > (apBuf.length - 1)) {
                            return true; // not enough characters
                        }
                        String snum1 = str.substring(i + 4, i + 4 + 2);
                        if (strNum.indexOf(snum1.charAt(0)) == -1
                                || strNum.indexOf(snum1.charAt(1)) == -1) {
                            return false;
                        }
                        int n1 = Integer.parseInt(snum1, 16);
                        String snum2 = str.substring(i + 7, i + 7 + 2);
                        if (strNum.indexOf(snum2.charAt(0)) == -1
                                || strNum.indexOf(snum2.charAt(1)) == -1) {
                            return false;
                        }
                        int n2 = Integer.parseInt(snum2, 16);
                        return !((n1 >= 128) && (n1 <= 191) && (n2 >= 128)
                                && (n2 <= 191));
                    }
                }
                i += 2;
            } else {
                if (count > 0) {
                    if (count % 2 != 0) {
                        return false;
                    }
                    count = 0;
                }
            }
        }
        return true;
    }

}
