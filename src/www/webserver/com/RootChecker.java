package www.webserver.com;

import java.io.File;

public class RootChecker {
    private static final String TAG = "RootChecker";
    private static volatile int rootStatus = -1;

    public static boolean isDeviceRooted() {
        if (rootStatus == -1) checkRoot();
        return rootStatus == 1;
    }

    private static synchronized void checkRoot() {
        if (rootStatus != -1) return;
        boolean found = false;
        for (String path : new String[]{
                "/system/bin/su", "/system/xbin/su", "/sbin/su",
                "/system/sbin/su", "/vendor/bin/su", "/su/bin/su"}) {
            if (new File(path).exists()) { found = true; break; }
        }
        rootStatus = found ? 1 : 0;
        AppLogger.log(TAG, "Root available: " + found);
    }
}