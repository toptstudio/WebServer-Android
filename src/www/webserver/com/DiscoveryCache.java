package www.webserver.com;

import android.content.Context;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class DiscoveryCache {
    private static final String CACHE_FILE = "discovery_cache.json";
    private static final int MAX_ENTRIES = 50;

    private final File cacheFile;

    public DiscoveryCache(Context context) {
        cacheFile = new File(context.getCacheDir(), CACHE_FILE);
    }

    public static class CachedServer {
        public final String ip;
        public final int port;
        public final long lastSeen;

        public CachedServer(String ip, int port, long lastSeen) {
            this.ip = ip;
            this.port = port;
            this.lastSeen = lastSeen;
        }
    }

    public synchronized List<CachedServer> loadAll() {
        List<CachedServer> list = new ArrayList<>();
        if (!cacheFile.exists()) return list;
        try {
            String content = readFileToString(cacheFile);
            JSONArray arr = new JSONArray(content);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                list.add(new CachedServer(
                        obj.getString("ip"),
                        obj.optInt("port", 0),
                        obj.optLong("lastSeen", 0)));
            }
            Collections.sort(list, (a, b) -> Long.compare(b.lastSeen, a.lastSeen));
        } catch (Exception e) {
            Log.w("DiscoveryCache", "Failed to load cache", e);
        }
        return list;
    }

    public synchronized void addOrUpdate(String ip, int port, long lastSeen) {
        List<CachedServer> list = loadAll();
        Iterator<CachedServer> it = list.iterator();
        while (it.hasNext()) {
            if (it.next().ip.equals(ip)) {
                it.remove();
                break;
            }
        }
        list.add(new CachedServer(ip, port, lastSeen));
        if (list.size() > MAX_ENTRIES) {
            list = list.subList(list.size() - MAX_ENTRIES, list.size());
        }
        saveList(list);
    }

    private void saveList(List<CachedServer> list) {
        JSONArray arr = new JSONArray();
        for (CachedServer s : list) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("ip", s.ip);
                obj.put("port", s.port);
                obj.put("lastSeen", s.lastSeen);
                arr.put(obj);
            } catch (Exception ignored) {}
        }
        try (FileOutputStream fos = new FileOutputStream(cacheFile)) {
            fos.write(arr.toString().getBytes("UTF-8"));
        } catch (Exception e) {
            // Log the failure instead of silently ignoring it (L8)
            Log.e("DiscoveryCache", "Failed to save cache", e);
        }
    }

    private String readFileToString(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int len;
            while ((len = fis.read(buf)) != -1) {
                bos.write(buf, 0, len);
            }
            return bos.toString("UTF-8");
        }
    }
}