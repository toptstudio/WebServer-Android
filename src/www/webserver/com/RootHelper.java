package www.webserver.com;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class RootHelper {
    private static final String TAG = "WebServer";
    private static final ExecutorService iptablesExecutor = Executors.newSingleThreadExecutor();

    public static boolean isDeviceRooted() {
        return RootChecker.isDeviceRooted();
    }

    public static Future<Boolean> setupPortForwarding(int targetPort) {
        return iptablesExecutor.submit(() -> {
            if (!isDeviceRooted()) return false;
            try {
                execSuWithTimeout("iptables -t nat -D PREROUTING -p tcp --dport 80 -j REDIRECT --to-port " + targetPort + " 2>/dev/null", 5);
                execSuWithTimeout("iptables -t nat -D PREROUTING -p tcp --dport 443 -j REDIRECT --to-port " + targetPort + " 2>/dev/null", 5);
                int result1 = execSuWithTimeout("iptables -t nat -A PREROUTING -p tcp --dport 80 -j REDIRECT --to-port " + targetPort, 5);
                int result2 = execSuWithTimeout("iptables -t nat -A PREROUTING -p tcp --dport 443 -j REDIRECT --to-port " + targetPort, 5);
                if (result1 == 0 && result2 == 0) {
                    return true;
                } else {
                    AppLogger.log(TAG, "Port forwarding command failed");
                    return false;
                }
            } catch (Exception e) {
                AppLogger.log(TAG, "Failed to set up port forwarding", e);
                return false;
            }
        });
    }

    public static void removePortForwarding(int targetPort) {
        iptablesExecutor.submit(() -> {
            try {
                execSuWithTimeout("iptables -t nat -D PREROUTING -p tcp --dport 80 -j REDIRECT --to-port " + targetPort + " 2>/dev/null", 5);
                execSuWithTimeout("iptables -t nat -D PREROUTING -p tcp --dport 443 -j REDIRECT --to-port " + targetPort + " 2>/dev/null", 5);
            } catch (Exception e) {
                AppLogger.log(TAG, "Failed to remove port forwarding", e);
            }
        });
    }

    private static int execSuWithTimeout(String command, int timeoutSeconds) throws Exception {
        Process process = new ProcessBuilder("su", "-c", command).start();
        final boolean[] finished = {false};
        Thread watcher = new Thread(() -> {
            try { process.waitFor(); } catch (InterruptedException ignored) {}
            finished[0] = true;
        });
        watcher.start();
        watcher.join(timeoutSeconds * 1000L);
        if (!finished[0]) {
            process.destroyForcibly();
            watcher.interrupt();
            throw new Exception("Root command timed out: " + command);
        }
        return process.exitValue();
    }
}