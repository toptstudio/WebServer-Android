package www.webserver.com;

import android.content.Context;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.*;

public class SettingsManager {
    private static final String SETTINGS_FILE = "webserver_settings.json";

    public static synchronized JSONObject loadSettings(Context context) {
        JSONObject json = new JSONObject();
        File file = new File(context.getFilesDir(), SETTINGS_FILE);
        if (!file.exists()) {
            try {
                json.put("port", 9999);
                json.put("ip", "0.0.0.0");
                json.put("https_enabled", false);
                json.put("storage_path", "");
                json.put("server_running", false);
                json.put("username", "");
                json.put("password", "");
                json.put("discovery_active", false);
                json.put("mode", "normal");
                json.put("host_html", false);
            } catch (JSONException ignored) {}
            return json;
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            String content = readAll(fis);
            if (content != null && !content.isEmpty()) json = new JSONObject(content);
        } catch (Exception ignored) {}

        try {
            if (!json.has("port")) json.put("port", 9999);
            if (!json.has("ip")) json.put("ip", "0.0.0.0");
            if (!json.has("https_enabled")) json.put("https_enabled", false);
            if (!json.has("storage_path")) json.put("storage_path", "");
            if (!json.has("server_running")) json.put("server_running", false);
            if (!json.has("username")) json.put("username", "");
            if (!json.has("password")) json.put("password", "");
            if (!json.has("discovery_active")) json.put("discovery_active", false);
            if (!json.has("mode")) json.put("mode", "normal");
            if (!json.has("host_html")) json.put("host_html", false);
        } catch (JSONException ignored) {}
        return json;
    }

    public static synchronized void saveSettings(Context context, JSONObject json) {
        File file = new File(context.getFilesDir(), SETTINGS_FILE);
        File tempFile = new File(context.getFilesDir(), SETTINGS_FILE + ".tmp");
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(json.toString().getBytes("UTF-8"));
            fos.flush();
            fos.getFD().sync();
        } catch (IOException e) {
            tempFile.delete();
            return;
        }
        if (tempFile.renameTo(file)) {
            return;
        }
        try (FileInputStream fis = new FileInputStream(tempFile);
             FileOutputStream fos = new FileOutputStream(file)) {
            byte[] buf = new byte[4096];
            int len;
            while ((len = fis.read(buf)) != -1) {
                fos.write(buf, 0, len);
            }
            fos.flush();
            fos.getFD().sync();
            tempFile.delete();
        } catch (IOException e) {
            tempFile.delete();
        }
    }

    private static String readAll(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[4096];
        int n;
        while ((n = is.read(data)) != -1) buffer.write(data, 0, n);
        return buffer.toString("UTF-8");
    }
}