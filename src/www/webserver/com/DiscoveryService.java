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
import android.util.Log;
import org.json.JSONObject;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class DiscoveryService extends Service {
    private static final String TAG = "DiscoverySvc";
    private static final String CHANNEL_ID = "discovery_channel";
    private static final String CHANNEL_ID_FOUND = "discovery_found_channel";
    private static final int NOTIFICATION_ID = 2001;
    // Fixed notification ID counter race (L6) – now using atomic update with wrap-around
    private static final AtomicInteger sNextFoundNotificationId = new AtomicInteger(3000);

    private static volatile boolean sActive = false;
    private static final CopyOnWriteArrayList<ProbeHelper.DiscoveredServer> sDiscoveredServers =
            new CopyOnWriteArrayList<>();
    private static final Set<String> sPendingResolveNames = Collections.synchronizedSet(new HashSet<>());
    private static final Object resolveLock = new Object(), probeLock = new Object();
    private static final Queue<NsdServiceInfo> resolveQueue = new LinkedList<>();
    private static final Queue<ProbeHelper.DiscoveredServer> probeQueue = new LinkedList<>();
    private static volatile boolean resolverRunning = false, probeThreadRunning = false;
    private static Thread resolverThread, probeThread;
    private static NsdManager.DiscoveryListener discoveryListener;
    private static final Object discoveryListenerLock = new Object();
    private static WifiManager.MulticastLock multicastLock;
    private static int autoRestartCount = 0;
    private static final int MAX_AUTO_RESTART = 5;

    private static volatile Context context;

    // Memory leak fix (M-2): always remove from this map on resolve failure
    private static final Map<String, AtomicInteger> resolveRetryCounts = new HashMap<>();

    public static boolean isActive() { return sActive; }

    public static void start(Context ctx) {
        if (sActive) return;
        context = ctx.getApplicationContext();
        Intent intent = new Intent(context, DiscoveryService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            context.startForegroundService(intent);
        else
            context.startService(intent);
    }

    public static void stop(Context ctx) {
        context = ctx.getApplicationContext();
        Intent intent = new Intent(context, DiscoveryService.class);
        context.stopService(intent);
        JSONObject settings = SettingsManager.loadSettings(context);
        try { settings.put("discovery_active", false); } catch (Exception ignored) {}
        SettingsManager.saveSettings(context, settings);
    }

    public static List<ProbeHelper.DiscoveredServer> getDiscoveredServers() {
        return new ArrayList<>(sDiscoveredServers);
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        context = this;
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            stopSelf();
            return;
        }
        createNotificationChannels();
        startForegroundWithType();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        context = this;
        if (intent != null && "STOP_DISCOVERY".equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (sActive) return START_STICKY;

        sActive = true;
        sDiscoveredServers.clear();
        sPendingResolveNames.clear();
        autoRestartCount = 0;

        JSONObject settings = SettingsManager.loadSettings(context);
        try { settings.put("discovery_active", true); } catch (Exception ignored) {}
        SettingsManager.saveSettings(context, settings);

        WifiManager wm = (WifiManager) getSystemService(WIFI_SERVICE);
        if (wm != null) {
            if (multicastLock != null && multicastLock.isHeld()) {
                multicastLock.release();
            }
            multicastLock = wm.createMulticastLock("discovery_mdns_lock");
            multicastLock.setReferenceCounted(false);
            multicastLock.acquire();
        }

        DiscoveryCache cache = new DiscoveryCache(this);
        List<DiscoveryCache.CachedServer> cached = cache.loadAll();
        int count = 0;
        for (DiscoveryCache.CachedServer cs : cached) {
            if (count >= 10) break;
            ProbeHelper.DiscoveredServer ds = new ProbeHelper.DiscoveredServer();
            ds.host = cs.ip;
            ds.port = cs.port;
            ds.probedProtocol = null;
            sDiscoveredServers.add(ds);
            enqueueProbe(ds);
            count++;
        }

        NsdManager nsd = (NsdManager) getSystemService(NSD_SERVICE);
        if (nsd != null) {
            synchronized (discoveryListenerLock) {
                discoveryListener = new DiscoveryServiceListener();
                nsd.discoverServices("_webserver._tcp", NsdManager.PROTOCOL_DNS_SD, discoveryListener);
            }
        }

        startResolverThread();
        startProbeThread();

        refreshNotification();
        return START_STICKY;
    }

    @Override public void onTaskRemoved(Intent rootIntent) {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            stopSelf();
            return;
        }
        Intent restartIntent = new Intent(getApplicationContext(), DiscoveryService.class);
        restartIntent.setPackage(getPackageName());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            startForegroundService(restartIntent);
        else
            startService(restartIntent);
        super.onTaskRemoved(rootIntent);
    }

    @Override public void onDestroy() {
        sActive = false;
        JSONObject settings = SettingsManager.loadSettings(context);
        try { settings.put("discovery_active", false); } catch (Exception ignored) {}
        SettingsManager.saveSettings(context, settings);

        if (multicastLock != null && multicastLock.isHeld()) multicastLock.release();
        synchronized (discoveryListenerLock) {
            if (discoveryListener != null) {
                NsdManager nsd = (NsdManager) getSystemService(NSD_SERVICE);
                if (nsd != null) try { nsd.stopServiceDiscovery(discoveryListener); } catch (Exception e) {}
            }
            discoveryListener = null;
        }

        stopProbeThread();
        stopResolverThread();
        sDiscoveredServers.clear();
        sPendingResolveNames.clear();
        clearFoundNotifications();
        if (Build.VERSION.SDK_INT >= 34) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        context = null;
        super.onDestroy();
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel ongoing = new NotificationChannel(
                    CHANNEL_ID, "Discovery Service", NotificationManager.IMPORTANCE_LOW);
            ongoing.setDescription("Ongoing discovery notification");
            nm.createNotificationChannel(ongoing);

            NotificationChannel found = new NotificationChannel(
                    CHANNEL_ID_FOUND, "Servers Found", NotificationManager.IMPORTANCE_HIGH);
            found.setDescription("Notifications for each discovered server");
            nm.createNotificationChannel(found);
        }
    }

    private void startForegroundWithType() {
        Notification n = buildNotification();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, n);
        }
    }

    private void refreshNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(NOTIFICATION_ID, buildNotification());
        startForegroundWithType();
    }

    private Notification buildNotification() {
        Intent stopIntent = new Intent(this, DiscoveryService.class);
        stopIntent.setAction("STOP_DISCOVERY");
        PendingIntent stopPI = PendingIntent.getService(this, 0, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));

        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPI = PendingIntent.getActivity(this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= 26) builder = new Notification.Builder(this, CHANNEL_ID);
        else builder = new Notification.Builder(this);

        return builder
                .setContentTitle("Discovering Servers")
                .setContentText("Searching for other instances...")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(openPI)
                .addAction(R.drawable.ic_notification, "Stop Discovery", stopPI)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
    }

    private void sendServerFoundNotification(ProbeHelper.DiscoveredServer server) {
        if (context == null) return;
        String proto = server.probedProtocol != null ? server.probedProtocol : "HTTP";
        String title = "Web Server " + proto;
        String body = server.host + ":" + server.port;
        String url = proto.toLowerCase() + "://" + server.host + ":" + server.port;

        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        // Atomic update with wrap-around to avoid duplicate IDs (L6)
        int uniqueId = sNextFoundNotificationId.getAndUpdate(id -> {
            int next = id + 1;
            if (next > 4000) next = 3000;
            return next;
        });
        PendingIntent browserPI = PendingIntent.getActivity(this, uniqueId, browserIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= 26) builder = new Notification.Builder(this, CHANNEL_ID_FOUND);
        else builder = new Notification.Builder(this);

        Notification n = builder
                .setContentTitle(title)
                .setContentText(body)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(browserPI)
                .setAutoCancel(true)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .build();

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(uniqueId, n);
    }

    private void clearFoundNotifications() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        for (int i = 3000; i < 4000; i++) {
            nm.cancel(i);
        }
    }

    private class DiscoveryServiceListener implements NsdManager.DiscoveryListener {
        @Override public void onStartDiscoveryFailed(String s, int i) {}
        @Override public void onStopDiscoveryFailed(String s, int i) {}
        @Override public void onDiscoveryStarted(String s) {}
        @Override public void onDiscoveryStopped(String s) {
            if (!sActive) return;
            if (autoRestartCount < MAX_AUTO_RESTART) {
                autoRestartCount++;
                if (restartHandler == null) restartHandler = new Handler(Looper.getMainLooper());
                restartHandler.postDelayed(new RestartDiscoveryRunnable(), 1000);
            } else {
                stopSelf();
            }
        }
        @Override public void onServiceFound(NsdServiceInfo si) {
            if (!sActive) return;
            String name = si.getServiceName();
            if (name == null) return;
            if (sPendingResolveNames.add(name)) {
                enqueueResolve(si);
            }
        }
        @Override public void onServiceLost(NsdServiceInfo si) {
            String name = si.getServiceName();
            if (name != null) {
                sPendingResolveNames.remove(name);
                sDiscoveredServers.removeIf(ds -> ds.name != null && ds.name.equals(name));
                synchronized (probeLock) {
                    probeQueue.removeIf(ds -> ds.name != null && ds.name.equals(name));
                }
            }
        }
    }

    private Handler restartHandler;

    private class RestartDiscoveryRunnable implements Runnable {
        @Override public void run() {
            if (sActive) {
                NsdManager nsd = (NsdManager) getSystemService(NSD_SERVICE);
                if (nsd != null) {
                    synchronized (discoveryListenerLock) {
                        nsd.discoverServices("_webserver._tcp", NsdManager.PROTOCOL_DNS_SD, discoveryListener);
                    }
                }
            }
        }
    }

    private void startResolverThread() {
        if (resolverThread != null && !resolverThread.isAlive()) {
            resolverRunning = false;
        }
        if (resolverRunning) return;
        resolverRunning = true;
        resolverThread = new Thread(new ResolverRunnable(), "Discovery-Resolver");
        resolverThread.start();
    }

    private class ResolverRunnable implements Runnable {
        @Override public void run() {
            while (resolverRunning) {
                NsdServiceInfo next;
                synchronized (resolveLock) {
                    if (resolveQueue.isEmpty()) {
                        try { resolveLock.wait(500); } catch (InterruptedException e) { break; }
                        continue;
                    }
                    next = resolveQueue.poll();
                }
                if (next != null && context != null) {
                    NsdManager nsd = (NsdManager) context.getSystemService(NSD_SERVICE);
                    if (nsd != null) {
                        nsd.resolveService(next, new ResolveListenerImpl());
                    }
                }
            }
        }
    }

    private class ResolveListenerImpl implements NsdManager.ResolveListener {
        @Override public void onResolveFailed(NsdServiceInfo s, int errorCode) {
            if (context == null || !sActive) return;
            String name = s.getServiceName();
            sPendingResolveNames.remove(name);
            if (errorCode == NsdManager.FAILURE_ALREADY_ACTIVE) {
                synchronized (resolveRetryCounts) {
                    AtomicInteger counter = resolveRetryCounts.get(name);
                    if (counter == null) {
                        counter = new AtomicInteger(0);
                        resolveRetryCounts.put(name, counter);
                    }
                    if (counter.incrementAndGet() <= 3) {
                        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                        enqueueResolve(s);
                    } else {
                        resolveRetryCounts.remove(name); // clean up after max retries (M-2)
                    }
                }
            } else {
                // For any other failure, remove the entry immediately (M-2)
                synchronized (resolveRetryCounts) {
                    resolveRetryCounts.remove(name);
                }
            }
        }

        @Override public void onServiceResolved(NsdServiceInfo s) {
            if (context == null || !sActive) return;
            String name = s.getServiceName();
            // Clean up retry map on success (M-2)
            synchronized (resolveRetryCounts) {
                resolveRetryCounts.remove(name);
            }
            if (s.getHost() == null) {
                sPendingResolveNames.remove(name);
                return;
            }
            String host = s.getHost().getHostAddress();
            int port = s.getPort();
            boolean exists = false;
            for (ProbeHelper.DiscoveredServer ds : sDiscoveredServers) {
                if (ds.host.equals(host) && ds.port == port) { exists = true; break; }
            }
            if (!exists) {
                ProbeHelper.DiscoveredServer server = new ProbeHelper.DiscoveredServer();
                server.name = name;
                server.host = host;
                server.port = port;
                server.probedProtocol = null;
                sPendingResolveNames.remove(server.name);
                sDiscoveredServers.add(server);
                enqueueProbe(server);
            }
        }
    }

    private void stopResolverThread() {
        resolverRunning = false;
        synchronized (resolveLock) { resolveQueue.clear(); resolveLock.notifyAll(); }
        if (resolverThread != null) { resolverThread.interrupt(); resolverThread = null; }
    }

    private void startProbeThread() {
        if (probeThread != null && !probeThread.isAlive()) {
            probeThreadRunning = false;
        }
        if (probeThreadRunning) return;
        probeThreadRunning = true;
        probeThread = new Thread(new ProbeRunnable(), "Discovery-Probe");
        probeThread.start();
    }

    private class ProbeRunnable implements Runnable {
        @Override public void run() {
            while (probeThreadRunning) {
                ProbeHelper.DiscoveredServer next;
                synchronized (probeLock) {
                    if (probeQueue.isEmpty()) {
                        try { probeLock.wait(500); } catch (InterruptedException e) { break; }
                        continue;
                    }
                    next = probeQueue.poll();
                }
                if (next != null && sActive) {
                    ProbeHelper.ProbeResult result = ProbeHelper.probe(next.host, next.port);
                    synchronized (sDiscoveredServers) {
                        sDiscoveredServers.remove(next);
                        if (result.httpOk || result.httpsOk) {
                            if (result.httpOk && result.httpsOk) {
                                ProbeHelper.DiscoveredServer httpServer = new ProbeHelper.DiscoveredServer();
                                httpServer.host = next.host; httpServer.port = next.port; httpServer.probedProtocol = "HTTP";
                                sDiscoveredServers.add(httpServer);
                                sendServerFoundNotification(httpServer);

                                ProbeHelper.DiscoveredServer httpsServer = new ProbeHelper.DiscoveredServer();
                                httpsServer.host = next.host; httpsServer.port = next.port; httpsServer.probedProtocol = "HTTPS";
                                sDiscoveredServers.add(httpsServer);
                                sendServerFoundNotification(httpsServer);
                            } else if (result.httpsOk) {
                                next.probedProtocol = "HTTPS"; sDiscoveredServers.add(next);
                                sendServerFoundNotification(next);
                            } else {
                                next.probedProtocol = "HTTP"; sDiscoveredServers.add(next);
                                sendServerFoundNotification(next);
                            }
                            if (context != null) {
                                DiscoveryCache cache = new DiscoveryCache(context);
                                cache.addOrUpdate(next.host, next.port, System.currentTimeMillis());
                            }
                        }
                    }
                }
            }
        }
    }

    private void stopProbeThread() {
        probeThreadRunning = false;
        synchronized (probeLock) { probeQueue.clear(); probeLock.notifyAll(); }
        if (probeThread != null) { probeThread.interrupt(); probeThread = null; }
    }

    private static void enqueueResolve(NsdServiceInfo si) {
        synchronized (resolveLock) { resolveQueue.add(si); resolveLock.notifyAll(); }
    }

    private static void enqueueProbe(ProbeHelper.DiscoveredServer server) {
        synchronized (probeLock) { probeQueue.add(server); probeLock.notifyAll(); }
    }
}