package www.webserver.com;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.net.*;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.os.*;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

public class ServerService extends Service implements NetworkMonitor.Listener {
    private static final String TAG = "WebServer";
    private static final String CHANNEL_ID = "webserver_channel";
    private static final int NOTIFICATION_ID = 1;
    public static final String ACTION_BIND_FAILED = "www.webserver.com.BIND_FAILED";
    public static final String ACTION_SERVER_STOPPED = "www.webserver.com.SERVER_STOPPED";

    private static final AtomicBoolean sIsRunning = new AtomicBoolean(false);
    public static boolean isRunning() { return sIsRunning.get(); }

    private static Context context;
    private volatile boolean stopped = false;
    private volatile Server mainServer;
    private Server localhostServer;
    private String currentIp = null;
    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;
    private final Object serverLock = new Object();
    private Handler retryHandler = new Handler(Looper.getMainLooper());
    private static final int MAX_RETRIES = 3;

    private NsdManager nsdManager;
    private NsdManager.RegistrationListener registrationListener;
    private String mServiceName = "WebServer";
    private boolean registrationInProgress = false;
    private Runnable registerRunnable;
    private String registrationInstanceSuffix = "";
    private boolean rootForwardingActive = false;

    private final BroadcastReceiver screenReceiver = new ScreenReceiver();
    private NetworkMonitor networkMonitor;
    private final AtomicBoolean localhostStarting = new AtomicBoolean(false);

    private class ScreenReceiver extends BroadcastReceiver {
        @Override public void onReceive(Context ctx, Intent intent) {
            refreshNotification();
        }
    }

    @Override public void onNetworkAvailable() {
        if (stopped) return;
        AppLogger.log(TAG, "Network available");
        restartServer(true);
    }

    @Override public void onNetworkLost() {
        if (stopped) return;
        AppLogger.log(TAG, "Network lost");
        stopMainServer();
    }

    @Override public void onIpChanged(String newIp) {
        if (stopped) return;
        AppLogger.log(TAG, "Display IP changed to " + newIp);
        currentIp = newIp;
        GlobalVars.ip = newIp;
        updateIpInSettings(newIp);
        broadcastIp(newIp);
    }

    private void restartServer(boolean determineIp) {
        retryHandler.removeCallbacksAndMessages(null);
        stopMainServer();
        if (determineIp) {
            currentIp = determineBestIp();
        }
        if (currentIp == null || "127.0.0.1".equals(currentIp)) {
            currentIp = "127.0.0.1";
            GlobalVars.ip = "127.0.0.1";
            updateIpInSettings("127.0.0.1");
            broadcastIp("127.0.0.1");
            return;
        }
        GlobalVars.ip = currentIp;
        updateIpInSettings(currentIp);
        broadcastIp(currentIp);
        startMainServer();
        retryHandler.post(() -> registerNsdService(GlobalVars.port));
    }

    private class NsdRegistrationListener implements NsdManager.RegistrationListener {
        @Override public void onRegistrationFailed(NsdServiceInfo si, int errorCode) {
            registrationInProgress = false;
        }
        @Override public void onUnregistrationFailed(NsdServiceInfo si, int errorCode) {}
        @Override public void onServiceRegistered(NsdServiceInfo si) {
            registrationInProgress = false;
        }
        @Override public void onServiceUnregistered(NsdServiceInfo si) {}
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        context = this;
        stopped = false;

        KeystoreProvider.ensureKeystore(this);

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            stopSelf(); return;
        }

        createNotificationChannel();
        startForegroundWithType();

        WifiManager wm = (WifiManager) getSystemService(WIFI_SERVICE);
        if (wm != null) {
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL, "WebServer::WifiLock");
            wifiLock.setReferenceCounted(false);
            wifiLock.acquire();
        }

        IntentFilter screenFilter = new IntentFilter();
        screenFilter.addAction(Intent.ACTION_SCREEN_OFF);
        screenFilter.addAction(Intent.ACTION_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 34) {
            registerReceiver(screenReceiver, screenFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(screenReceiver, screenFilter);
        }

        sIsRunning.set(true);
    }

    private void refreshNotification() {
        if (!hasNotificationPermission()) { stopSelf(); return; }
        Notification notification = buildNotification();
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(NOTIFICATION_ID, notification);
        startForegroundWithType();
    }

    private void startForegroundWithType() {
        if (!hasNotificationPermission()) { stopSelf(); return; }
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE | ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33)
            return checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        return true;
    }

    public void onTimeout(int startId, int fgsType) {}

    @Override public void onDestroy() {
        stopped = true;
        sIsRunning.set(false);
        if (networkMonitor != null) {
            networkMonitor.stop();
            networkMonitor = null;
        }
        stopMainServer();
        stopLocalhostServer();
        unregisterNsdService();
        removeRootPortForwarding();
        releaseWakeLock();
        if (wifiLock != null) {
            try { wifiLock.release(); } catch (Exception ignored) {}
            wifiLock = null;
        }
        try { unregisterReceiver(screenReceiver); } catch (Exception ignored) {}
        retryHandler.removeCallbacksAndMessages(null);
        if (Build.VERSION.SDK_INT >= 34) {
            stopForeground(Service.STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        broadcastServerStopped();
        context = null;
        super.onDestroy();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        context = this;
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            stopSelf();
            return START_NOT_STICKY;
        }

        stopped = false;

        JSONObject settings = SettingsManager.loadSettings(context);
        if (!settings.optBoolean("server_running", false)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        loadData();

        if (intent != null) {
            startForegroundWithType();
        } else {
            refreshNotification();
        }

        GlobalVars.started = true;

        setupRootPortForwarding();

        synchronized (serverLock) {
            if (localhostServer == null && !localhostStarting.getAndSet(true)) {
                startLocalhostServer(GlobalVars.port);
            }
        }

        restartServer(true);

        if (networkMonitor == null) {
            networkMonitor = new NetworkMonitor(this, this);
            networkMonitor.start();
        }

        if (settings.optBoolean("discovery_active", false) && !DiscoveryService.isActive()) {
            DiscoveryService.start(this);
        }

        return START_NOT_STICKY;
    }

    @Override public void onTaskRemoved(Intent rootIntent) {
        Intent restartIntent = new Intent(getApplicationContext(), ServerService.class);
        restartIntent.setPackage(getPackageName());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(restartIntent);
        else startService(restartIntent);
        super.onTaskRemoved(rootIntent);
    }

    private String determineBestIp() {
        String wifiIp = getWifiClientIp(); if (wifiIp != null) return wifiIp;
        String apIp = findInterfaceIp("ap0","wlan1","swlan0"); if (apIp != null) return apIp;
        String cellIp = getCellularIp(); if (cellIp != null) return cellIp;
        String v6Ip = getFirstIpv6(); if (v6Ip != null) return v6Ip;
        return null;
    }

    private String getWifiClientIp() {
        if (Build.VERSION.SDK_INT >= 21) {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (cm != null) {
                Network network = cm.getActiveNetwork();
                if (network != null && cm.getNetworkCapabilities(network) != null &&
                    cm.getNetworkCapabilities(network).hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    LinkProperties lp = cm.getLinkProperties(network);
                    if (lp != null) for (LinkAddress addr : lp.getLinkAddresses()) {
                        InetAddress ia = addr.getAddress();
                        if (ia instanceof Inet4Address && !ia.isLoopbackAddress()) {
                            String ip = ia.getHostAddress();
                            if (isIpValid(ip)) return ip;
                        }
                    }
                }
            }
        }
        try {
            WifiManager wm = (WifiManager) getSystemService(WIFI_SERVICE);
            if (wm != null && wm.isWifiEnabled()) {
                android.net.wifi.WifiInfo info = wm.getConnectionInfo();
                if (info != null) { int ipInt = info.getIpAddress(); if (ipInt != 0) { String ip = intToIp(ipInt); if (isIpValid(ip)) return ip; } }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String getCellularIp() {
        if (Build.VERSION.SDK_INT >= 21) {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (cm != null) for (Network network : cm.getAllNetworks()) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    LinkProperties lp = cm.getLinkProperties(network);
                    if (lp != null) for (LinkAddress addr : lp.getLinkAddresses()) {
                        InetAddress ia = addr.getAddress();
                        if (ia instanceof Inet4Address && !ia.isLoopbackAddress()) {
                            String ip = ia.getHostAddress();
                            if (isIpValid(ip)) return ip;
                        }
                    }
                }
            }
        }
        return findInterfaceIp("rmnet0","rmnet1","rmnet2","rmnet_data0","rmnet_data1","wwan0","pdp0","ccmni0","usb0","rndis0");
    }

    private String findInterfaceIp(String... preferred) {
        try {
            for (String p : preferred) {
                Enumeration<NetworkInterface> ifEnum = NetworkInterface.getNetworkInterfaces();
                while (ifEnum.hasMoreElements()) {
                    NetworkInterface ni = ifEnum.nextElement();
                    if (ni.getName().equalsIgnoreCase(p) && ni.isUp()) { String ip = getFirstIpv4(ni); if (ip != null) return ip; }
                }
            }
            Enumeration<NetworkInterface> all = NetworkInterface.getNetworkInterfaces();
            while (all.hasMoreElements()) {
                NetworkInterface ni = all.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                String ip = getFirstIpv4(ni); if (ip != null) return ip;
            }
        } catch (SocketException ignored) {}
        return null;
    }

    private String getFirstIpv4(NetworkInterface ni) {
        Enumeration<InetAddress> addrs = ni.getInetAddresses();
        while (addrs.hasMoreElements()) {
            InetAddress ia = addrs.nextElement();
            if (ia instanceof Inet4Address && !ia.isLoopbackAddress()) { String ip = ia.getHostAddress(); if (isIpValid(ip)) return ip; }
        }
        return null;
    }

    private String getFirstIpv6() {
        try {
            Enumeration<NetworkInterface> all = NetworkInterface.getNetworkInterfaces();
            while (all.hasMoreElements()) {
                NetworkInterface ni = all.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress ia = addrs.nextElement();
                    if (ia instanceof Inet6Address && !ia.isLoopbackAddress() && !ia.isLinkLocalAddress() && !ia.isMulticastAddress()) {
                        String ip = ia.getHostAddress();
                        if (ip != null && !ip.contains("::1")) return ip;
                    }
                }
            }
        } catch (SocketException ignored) {}
        return null;
    }

    private void broadcastIp(String ipv4) {
        Intent intent = new Intent("www.webserver.com.IP_UPDATED");
        intent.putExtra("ip", ipv4 != null ? ipv4 : "");
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    private void setupRootPortForwarding() {
        if (RootChecker.isDeviceRooted()) {
            rootForwardingActive = true;
            Future<Boolean> result = RootHelper.setupPortForwarding(GlobalVars.port);
            new Thread(() -> {
                try {
                    if (!result.get()) {
                        retryHandler.post(() -> Toast.makeText(context, "Port forwarding failed", Toast.LENGTH_SHORT).show());
                    }
                } catch (Exception e) {
                    retryHandler.post(() -> Toast.makeText(context, "Port forwarding error", Toast.LENGTH_SHORT).show());
                }
            }).start();
        }
    }

    private void removeRootPortForwarding() {
        if (rootForwardingActive) {
            RootHelper.removePortForwarding(GlobalVars.port);
            rootForwardingActive = false;
        }
    }

    private void registerNsdService(int port) {
        if (registerRunnable != null) retryHandler.removeCallbacks(registerRunnable);
        registrationInProgress = false;
        unregisterNsdService();
        registrationInstanceSuffix = String.valueOf(System.currentTimeMillis());
        final int servicePort = port;
        registerRunnable = () -> {
            registrationInProgress = true;
            nsdManager = (NsdManager) getSystemService(NSD_SERVICE);
            if (nsdManager == null) { registrationInProgress = false; return; }
            NsdServiceInfo serviceInfo = new NsdServiceInfo();
            serviceInfo.setServiceName(mServiceName + "_" + registrationInstanceSuffix);
            serviceInfo.setServiceType("_webserver._tcp");
            serviceInfo.setPort(servicePort);
            registrationListener = new NsdRegistrationListener();
            try {
                nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener);
            } catch (Exception e) {
                registrationInProgress = false;
            }
        };
        retryHandler.postDelayed(registerRunnable, 600);
    }

    private void unregisterNsdService() {
        if (nsdManager != null && registrationListener != null) {
            try { nsdManager.unregisterService(registrationListener); } catch (Exception e) {}
        }
        registrationListener = null;
    }

    private void startLocalhostServer(int port) {
        synchronized (serverLock) {
            if (stopped) { localhostStarting.set(false); return; }
            try {
                if (localhostServer != null) { localhostStarting.set(false); return; }
                localhostServer = new Server("127.0.0.1", port, GlobalVars.httpsEnabled);
                localhostServer.start();
            } catch (Exception e) {
                if (!stopped) AppLogger.log(TAG, "Localhost server failed", e);
            } finally {
                localhostStarting.set(false);
            }
        }
    }

    private void stopLocalhostServer() {
        synchronized (serverLock) {
            if (localhostServer != null) {
                localhostServer.stopServer();
                localhostServer = null;
            }
        }
        localhostStarting.set(false);
    }

    private void startMainServer() {
        if (stopped || currentIp == null || "127.0.0.1".equals(currentIp)) return;
        acquireWakeLock();
        new Thread(() -> {
            int attempt = 0;
            while (attempt < MAX_RETRIES && !stopped) {
                try {
                    synchronized (serverLock) {
                        if (stopped) return;
                        if (mainServer != null) {
                            stopMainServer();
                        }
                        mainServer = new Server(currentIp, GlobalVars.port, GlobalVars.httpsEnabled);
                        mainServer.start();
                    }
                    broadcastIp(currentIp);
                    retryHandler.post(() -> registerNsdService(GlobalVars.port));
                    return;
                } catch (java.security.KeyStoreException | java.security.NoSuchAlgorithmException | java.security.cert.CertificateException | java.security.UnrecoverableKeyException e) {
                    retryHandler.post(() -> Toast.makeText(context, "SSL certificate error – check keystore.", Toast.LENGTH_LONG).show());
                    sendBindFailure(); stopSelf(); releaseWakeLock(); return;
                } catch (IOException e) {
                    if (stopped) { releaseWakeLock(); return; }
                    synchronized (serverLock) { if (mainServer != null) { mainServer.stopServer(); mainServer = null; } }
                    try { Thread.sleep(500); } catch (InterruptedException ie) { releaseWakeLock(); return; }
                    attempt++;
                } catch (Exception e) {
                    break;
                }
            }
            if (!stopped) {
                retryHandler.post(() -> {
                    Toast.makeText(context, "Port " + GlobalVars.port + " is busy – please wait and try again.", Toast.LENGTH_LONG).show();
                    sendBindFailure(); stopSelf();
                });
                releaseWakeLock();
            }
        }).start();
    }

    private void stopMainServer() {
        synchronized (serverLock) {
            if (mainServer != null) {
                mainServer.stopServer();
                mainServer = null;
            }
        }
        unregisterNsdService();
        if (registerRunnable != null) retryHandler.removeCallbacks(registerRunnable);
        releaseWakeLock();
    }

    private void sendBindFailure() {
        Intent intent = new Intent(ACTION_BIND_FAILED); intent.setPackage(getPackageName()); sendBroadcast(intent);
    }

    private void broadcastServerStopped() {
        Intent intent = new Intent(ACTION_SERVER_STOPPED); intent.setPackage(getPackageName()); sendBroadcast(intent);
    }

    private void updateIpInSettings(String ip) {
        JSONObject settings = SettingsManager.loadSettings(context);
        try { settings.put("ip", ip); } catch (Exception ignored) {}
        SettingsManager.saveSettings(context, settings);
    }

    private void acquireWakeLock() {
        if (wakeLock == null) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WebServer::WakeLock");
        }
        if (!wakeLock.isHeld()) {
            wakeLock.acquire();
        }
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
            } catch (Exception ignored) {}
        }
    }

    private void loadData() {
        JSONObject settings = SettingsManager.loadSettings(context);
        GlobalVars.port = settings.optInt("port", 9999);
        GlobalVars.httpsEnabled = settings.optBoolean("https_enabled", false);
        GlobalVars.hostHtmlEnabled = settings.optBoolean("host_html", false);

        String mode = settings.optString("mode", "normal");
        if ("saf".equals(mode)) {
            String uriStr = settings.optString("root_uri", "");
            if (!uriStr.isEmpty()) {
                Uri uri = Uri.parse(uriStr);
                try {
                    context.getContentResolver().takePersistableUriPermission(uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    GlobalVars.rootUri = uri;
                    GlobalVars.legacyPath = null;
                } catch (SecurityException e) {
                    GlobalVars.rootUri = null;
                }
            } else {
                GlobalVars.rootUri = null;
            }
        } else {
            String path = settings.optString("storage_path", "");
            if (path.isEmpty()) {
                path = Environment.getExternalStorageDirectory().getAbsolutePath();
                if (!path.endsWith("/")) path += "/";
            }
            GlobalVars.legacyPath = path;
            GlobalVars.rootUri = null;
        }
    }

    private String intToIp(int ip) {
        return (ip & 0xFF) + "." + ((ip >> 8) & 0xFF) + "." + ((ip >> 16) & 0xFF) + "." + ((ip >> 24) & 0xFF);
    }

    private boolean isIpValid(String ip) {
        if (ip == null || ip.isEmpty()) return false;
        if (ip.equals("0.0.0.0") || ip.equals("127.0.0.1")) return false;
        if (ip.startsWith("169.254.")) return false;
        return true;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Web Server Service", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Keeps web server running");
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private static final int PENDING_FLAGS = Build.VERSION.SDK_INT >= 23 ?
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT :
            PendingIntent.FLAG_UPDATE_CURRENT;

    private Notification buildNotification() {
        Intent stopIntent = new Intent(this, NotificationActionReceiver.class);
        stopIntent.setAction(NotificationActionReceiver.ACTION_STOP_SERVER);
        stopIntent.setPackage(getPackageName());
        PendingIntent stopPendingIntent = PendingIntent.getBroadcast(this, 1, stopIntent, PENDING_FLAGS);

        Intent contentIntent = new Intent(this, MainActivity.class);
        contentIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentPendingIntent = PendingIntent.getActivity(this, 0, contentIntent, PENDING_FLAGS);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= 26) builder = new Notification.Builder(this, CHANNEL_ID);
        else builder = new Notification.Builder(this);
        String proto = GlobalVars.httpsEnabled ? "HTTPS" : "HTTP";

        return builder
            .setContentTitle("Web Server " + proto)
            .setContentText("Serving on port " + GlobalVars.port)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(contentPendingIntent)
            .addAction(R.drawable.ic_notification, "Stop Server", stopPendingIntent)
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .build();
    }
}