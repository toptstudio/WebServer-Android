package www.webserver.com;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Environment;
import org.json.JSONObject;

public class MainReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (GlobalVars.appContext == null) GlobalVars.appContext = context.getApplicationContext();

        if (Build.VERSION.SDK_INT >= 33) {
            if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) return;
        }

        JSONObject settings = SettingsManager.loadSettings(context);

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
            GlobalVars.rootUri = null;
            String storagePath = settings.optString("storage_path", "");
            if (storagePath.isEmpty()) {
                storagePath = Environment.getExternalStorageDirectory().getAbsolutePath();
                if (!storagePath.endsWith("/")) storagePath += "/";
            }
            GlobalVars.legacyPath = storagePath;
        }

        boolean shouldRun = settings.optBoolean("server_running", false);
        if (shouldRun) {
            int port = settings.optInt("port", 9999);
            boolean httpsEnabled = settings.optBoolean("https_enabled", false);
            GlobalVars.port = port;
            GlobalVars.httpsEnabled = httpsEnabled;

            String strIntAIp = GlobalVars.intAIp(((WifiManager) GlobalVars.appContext.getSystemService(Context.WIFI_SERVICE))
                    .getConnectionInfo().getIpAddress());
            if (!strIntAIp.contains("0.0.0.0")) GlobalVars.ip = strIntAIp;

            Intent serviceIntent = new Intent(context, ServerService.class);
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(serviceIntent);
            else context.startService(serviceIntent);
        }

        boolean discoveryWasActive = settings.optBoolean("discovery_active", false);
        if (discoveryWasActive) {
            DiscoveryService.start(context);
        }
    }
}