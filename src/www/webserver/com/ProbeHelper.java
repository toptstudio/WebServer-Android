package www.webserver.com;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class ProbeHelper {
    private static final String TAG = "WebServer";
    private static final int PROBE_TIMEOUT_MS = 2000;

    public static class ProbeResult {
        public boolean httpOk;
        public boolean httpsOk;
    }

    public static class DiscoveredServer {
        public String name;
        public String host;
        public int port;
        public boolean httpOk;
        public boolean httpsOk;
        public String probedProtocol;
    }

    public static ProbeResult probe(String host, int port) {
        ProbeResult result = new ProbeResult();
        result.httpOk = probeHttp(host, port);
        result.httpsOk = probeHttps(host, port);
        return result;
    }

    private static boolean probeHttp(String host, int port) {
        Socket socket = null;
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), PROBE_TIMEOUT_MS);
            socket.setSoTimeout(PROBE_TIMEOUT_MS);
            OutputStream out = socket.getOutputStream();
            out.write("GET / HTTP/1.0\r\nHost: ".getBytes("UTF-8"));
            out.write(host.getBytes("UTF-8"));
            out.write("\r\nConnection: close\r\n\r\n".getBytes("UTF-8"));
            out.flush();
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), "ISO-8859-1"));
            String line = reader.readLine();
            return line != null && line.startsWith("HTTP/");
        } catch (Exception e) {
            AppLogger.log(TAG, "HTTP probe failed for " + host + ":" + port + ": " + e.getMessage());
        } finally {
            if (socket != null) try { socket.close(); } catch (Exception ignored) {}
        }
        return false;
    }

    private static boolean probeHttps(String host, int port) {
        SSLSocket sslSocket = null;
        try {
            TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
            };
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            SSLSocketFactory factory = sslContext.getSocketFactory();

            sslSocket = (SSLSocket) factory.createSocket();
            sslSocket.connect(new InetSocketAddress(host, port), PROBE_TIMEOUT_MS);
            sslSocket.setSoTimeout(PROBE_TIMEOUT_MS);
            sslSocket.startHandshake();

            OutputStream out = sslSocket.getOutputStream();
            out.write("GET / HTTP/1.0\r\nHost: ".getBytes("UTF-8"));
            out.write(host.getBytes("UTF-8"));
            out.write("\r\nConnection: close\r\n\r\n".getBytes("UTF-8"));
            out.flush();

            BufferedReader reader = new BufferedReader(
                new InputStreamReader(sslSocket.getInputStream(), "ISO-8859-1"));
            String line = reader.readLine();
            if (line != null && line.startsWith("HTTP/")) {
                return true;
            }
        } catch (Exception e) {
            AppLogger.log(TAG, "HTTPS probe failed for " + host + ":" + port + ": " + e.getMessage());
        } finally {
            if (sslSocket != null) try { sslSocket.close(); } catch (Exception ignored) {}
        }
        return false;
    }
}