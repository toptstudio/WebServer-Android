package www.webserver.com;

import android.content.Context;
import android.util.Base64;
import java.io.*;
import java.security.KeyStore;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;

public class KeystoreProvider {
    private static final String TAG = "KeystoreProvider";
    private static final String KEYSTORE_FILENAME = "keystore.p12";
    private static final String KEYSTORE_PASSWORD = "x9K#mP2$vL7qR8!s";
    private static final String BASE64_KEYSTORE =
        "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";

    private static volatile boolean extracted = false;
    private static volatile Exception extractionError = null;

    public static synchronized void ensureKeystore(Context context) {
        if (extracted) return;
        try {
            File outFile = new File(context.getFilesDir(), KEYSTORE_FILENAME);
            if (!outFile.exists()) {
                byte[] keystoreBytes = Base64.decode(BASE64_KEYSTORE, Base64.DEFAULT);
                try (FileOutputStream fos = new FileOutputStream(outFile)) {
                    fos.write(keystoreBytes);
                    fos.flush();
                }
            }
            extracted = true;
        } catch (Exception e) {
            extractionError = e;
            AppLogger.log(TAG, "Failed to extract embedded keystore", e);
        }
    }

    public static KeyStore getKeyStore(Context context) throws Exception {
        if (extractionError != null) throw extractionError;
        if (!extracted) ensureKeystore(context);

        File keystoreFile = new File(context.getFilesDir(), KEYSTORE_FILENAME);
        if (!keystoreFile.exists()) throw new FileNotFoundException("Keystore file missing");

        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream is = new FileInputStream(keystoreFile)) {
            ks.load(is, KEYSTORE_PASSWORD.toCharArray());
        }
        return ks;
    }

    public static SSLServerSocketFactory getSSLServerSocketFactory(Context context) throws Exception {
        KeyStore ks = getKeyStore(context);
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("X509");
        kmf.init(ks, KEYSTORE_PASSWORD.toCharArray());

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), null, null);
        return sslContext.getServerSocketFactory();
    }
}