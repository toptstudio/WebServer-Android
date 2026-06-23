package www.webserver.com;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.Looper;

import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.concurrent.atomic.AtomicBoolean;

public class NetworkMonitor {

    public interface Listener {
        void onNetworkAvailable();
        void onNetworkLost();
        void onIpChanged(String newIp);
    }

    private final Context context;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ConnectivityManager.NetworkCallback networkCallback;
    private final AtomicBoolean currentNetworkAvailable = new AtomicBoolean(false);
    private String lastKnownIp = null;
    private volatile boolean started = false;

    private static final long DEBOUNCE_MS = 1000;
    private long lastAvailableTime = 0;
    private long lastIpChangeTime = 0;

    public NetworkMonitor(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void start() {
        if (started) return;
        started = true;

        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return;

        NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
                .build();

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                long now = System.currentTimeMillis();
                if (now - lastAvailableTime < DEBOUNCE_MS) return;
                lastAvailableTime = now;

                currentNetworkAvailable.set(true);
                listener.onNetworkAvailable();
                handler.post(NetworkMonitor.this::checkIpChange);
            }

            @Override
            public void onLost(Network network) {
                currentNetworkAvailable.set(false);
                listener.onNetworkLost();
            }

            @Override
            public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
                if (caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    currentNetworkAvailable.set(true);
                }
                handler.post(NetworkMonitor.this::checkIpChange);
            }

            @Override
            public void onLinkPropertiesChanged(Network network, android.net.LinkProperties lp) {
                handler.post(NetworkMonitor.this::checkIpChange);
            }
        };

        cm.registerNetworkCallback(request, networkCallback);
        handler.post(this::checkIpChange);
    }

    public void stop() {
        started = false;
        if (networkCallback != null) {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                cm.unregisterNetworkCallback(networkCallback);
            }
            networkCallback = null;
        }
        handler.removeCallbacksAndMessages(null);
    }

    public boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        Network activeNetwork = cm.getActiveNetwork();
        if (activeNetwork == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(activeNetwork);
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private void checkIpChange() {
        if (!started) return;
        long now = System.currentTimeMillis();
        if (now - lastIpChangeTime < DEBOUNCE_MS) return;
        lastIpChangeTime = now;

        String currentIp = determineBestIp();
        if (currentIp == null) {
            currentIp = "127.0.0.1";
        }
        if (!currentIp.equals(lastKnownIp)) {
            lastKnownIp = currentIp;
            listener.onIpChanged(currentIp);
        }
    }

    private String determineBestIp() {
        try {
            String wifiIp = getWifiClientIp();
            if (wifiIp != null) return wifiIp;
            String apIp = findInterfaceIp("ap0", "wlan1", "swlan0");
            if (apIp != null) return apIp;
            String cellIp = getCellularIp();
            if (cellIp != null) return cellIp;
        } catch (Exception ignored) {}
        return null;
    }

    private String getWifiClientIp() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface ni = en.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                String name = ni.getName();
                if (name.startsWith("wlan") && !name.contains("p2p") && !name.contains("ap")) {
                    for (InterfaceAddress addr : ni.getInterfaceAddresses()) {
                        InetAddress ia = addr.getAddress();
                        if (ia instanceof java.net.Inet4Address && !ia.isLoopbackAddress()) {
                            String ip = ia.getHostAddress();
                            if (isValidIp(ip)) return ip;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String getCellularIp() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface ni = en.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                String name = ni.getName();
                if (name.startsWith("rmnet") || name.startsWith("wwan") || name.startsWith("ccmni")) {
                    for (InterfaceAddress addr : ni.getInterfaceAddresses()) {
                        InetAddress ia = addr.getAddress();
                        if (ia instanceof java.net.Inet4Address && !ia.isLoopbackAddress()) {
                            String ip = ia.getHostAddress();
                            if (isValidIp(ip)) return ip;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String findInterfaceIp(String... names) {
        try {
            for (String name : names) {
                NetworkInterface ni = NetworkInterface.getByName(name);
                if (ni != null && ni.isUp()) {
                    for (InterfaceAddress addr : ni.getInterfaceAddresses()) {
                        InetAddress ia = addr.getAddress();
                        if (ia instanceof java.net.Inet4Address && !ia.isLoopbackAddress()) {
                            String ip = ia.getHostAddress();
                            if (isValidIp(ip)) return ip;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private boolean isValidIp(String ip) {
        if (ip == null || ip.isEmpty()) return false;
        if (ip.equals("0.0.0.0") || ip.equals("127.0.0.1")) return false;
        if (ip.startsWith("169.254.")) return false;
        return true;
    }
}