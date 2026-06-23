package www.webserver.com;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;

public class Server extends Thread {
    private final CopyOnWriteArrayList<Socket> clientList = new CopyOnWriteArrayList<>();
    private ServerSocket listener;
    private volatile boolean running = true;

    public Server(String ip, int port, boolean useHttps) throws Exception {
        if (useHttps) {
            SSLServerSocketFactory factory = KeystoreProvider.getSSLServerSocketFactory(GlobalVars.appContext);
            SSLServerSocket sslSocket = (SSLServerSocket) factory.createServerSocket();
            sslSocket.setReuseAddress(true);
            sslSocket.bind(new InetSocketAddress(InetAddress.getByName(ip), port), 50);
            configureSSL(sslSocket);
            listener = sslSocket;
        } else {
            ServerSocket ss = new ServerSocket();
            ss.setReuseAddress(true);
            ss.bind(new InetSocketAddress(InetAddress.getByName(ip), port), 50);
            listener = ss;
        }
    }

    private void configureSSL(SSLServerSocket sslSocket) {
        List<String> protocols = new ArrayList<>();
        for (String p : sslSocket.getSupportedProtocols()) {
            if (p.equals("SSLv3") || p.equals("SSLv2Hello")) continue;
            protocols.add(p);
        }
        sslSocket.setEnabledProtocols(protocols.toArray(new String[0]));

        List<String> allCiphers = new ArrayList<>();
        for (String c : sslSocket.getSupportedCipherSuites()) {
            allCiphers.add(c);
        }
        List<String> filtered = new ArrayList<>();
        for (String c : allCiphers) {
            if (c.contains("_WITH_NULL_") || c.contains("_anon_") ||
                c.contains("_EXPORT_") || c.contains("_DES_")) continue;
            filtered.add(c);
        }
        if (filtered.isEmpty()) {
            for (String c : allCiphers) {
                if (c.contains("_WITH_AES_") && c.contains("_GCM_")) {
                    filtered.add(c);
                    break;
                }
            }
            if (filtered.isEmpty()) filtered.add(allCiphers.get(0));
        }
        sslSocket.setEnabledCipherSuites(filtered.toArray(new String[0]));
    }

    @Override
    public void run() {
        while (running) {
            try {
                Socket socket = listener.accept();
                if (GlobalVars.highSpeedMode) {
                    socket.setSendBufferSize(262144);
                    socket.setReceiveBufferSize(262144);
                    socket.setTcpNoDelay(true);
                }
                try {
                    ServerHandler handler = new ServerHandler(socket, this);
                    handler.start();
                    clientList.add(socket);
                } catch (Throwable t) {
                    try { socket.close(); } catch (IOException ignored) {}
                    AppLogger.log("WebServer", "Failed to create handler", t);
                }
            } catch (IOException e) {
                if (!running) break;
                if (listener.isClosed()) {
                    running = false;
                    AppLogger.log("WebServer", "Server socket closed – stopping");
                    break;
                }
                AppLogger.log("WebServer", "Accept failed (non-close)", e);
                try { Thread.sleep(100); } catch (InterruptedException ie) { break; }
            }
        }
    }

    public void stopServer() {
        running = false;
        if (listener != null && !listener.isClosed()) {
            try { listener.close(); } catch (IOException ignored) {}
        }
        for (Socket s : clientList) {
            try { s.close(); } catch (IOException ignored) {}
        }
        clientList.clear();
    }

    public void removeClient(Socket socket) {
        clientList.remove(socket);
    }
}