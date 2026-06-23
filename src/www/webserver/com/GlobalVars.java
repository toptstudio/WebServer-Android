package www.webserver.com;

import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.net.Uri;

public class GlobalVars extends Application {
    public static Context appContext;
    public static volatile boolean started = false;
    public static volatile int port = 9999;
    public static volatile String ip = null;
    public static volatile Uri rootUri = null;
    public static volatile String legacyPath = null;
    public static volatile int bufferSizeKB = 64;
    public static volatile boolean httpsEnabled = false;
    public static volatile boolean highSpeedMode = false;
    public static volatile boolean hostHtmlEnabled = false;

    @Override
    public void onCreate() {
        super.onCreate();
        appContext = getApplicationContext();
        determineOptimalBufferSettings();
    }

    public static String intAIp(int i) {
        return (i & 0xFF) + "." + ((i >> 8) & 0xFF) + "." + ((i >> 16) & 0xFF) + "." + ((i >> 24) & 0xFF);
    }

    private static ActivityManager getActivityManager() {
        if (appContext == null) return null;
        return (ActivityManager) appContext.getSystemService(Context.ACTIVITY_SERVICE);
    }

    public static long getTotalRamInMB() {
        ActivityManager am = getActivityManager();
        if (am == null) return 2048L;
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return mi.totalMem / (1024 * 1024);
    }

    public static long getAvailableRamInMB() {
        ActivityManager am = getActivityManager();
        if (am == null) return 512L;
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return mi.availMem / (1024 * 1024);
    }

    public static boolean isLowRamDevice() {
        ActivityManager am = getActivityManager();
        if (am == null) return true;
        return am.isLowRamDevice() || getTotalRamInMB() <= 1024;
    }

    public static int getRecommendedMaxBufferKB() {
        long totalRam = getTotalRamInMB();
        long availRam = getAvailableRamInMB();
        double ratio;
        if (totalRam < 2048) ratio = 0.002;
        else if (isLowRamDevice()) ratio = 0.003;
        else ratio = 0.005;
        int maxKB = (int) (ratio * availRam * 1024);
        if (maxKB < 64) maxKB = 64;
        if (maxKB > 4096) maxKB = 4096;
        return maxKB;
    }

    public static void determineOptimalBufferSettings() {
        long totalRam = getTotalRamInMB();
        if (totalRam <= 1024) bufferSizeKB = 64;
        else if (totalRam <= 1536) bufferSizeKB = 96;
        else if (totalRam <= 2048) bufferSizeKB = 128;
        else if (totalRam <= 3072) bufferSizeKB = 192;
        else if (totalRam <= 4096) bufferSizeKB = 256;
        else if (totalRam <= 6144) bufferSizeKB = 384;
        else if (totalRam <= 8192) bufferSizeKB = 512;
        else bufferSizeKB = 512;
        int maxRecommended = getRecommendedMaxBufferKB();
        if (bufferSizeKB > maxRecommended) bufferSizeKB = maxRecommended;
        if (bufferSizeKB > 512) bufferSizeKB = 512;
    }
}