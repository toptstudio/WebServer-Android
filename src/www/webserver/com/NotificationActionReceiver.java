package www.webserver.com;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import org.json.JSONObject;

public class NotificationActionReceiver extends BroadcastReceiver {
    public static final String ACTION_STOP_SERVER = "www.webserver.com.STOP_SERVER";
    public static final String ACTION_OPEN_UI = "www.webserver.com.OPEN_UI";
    public static final String ACTION_SERVER_STOPPED = "www.webserver.com.SERVER_STOPPED";

    private static class StopToastRunnable implements Runnable {
        private final Context context;
        StopToastRunnable(Context context) { this.context = context; }
        @Override
        public void run() { Toast.makeText(context, "Server stopped", Toast.LENGTH_SHORT).show(); }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;
        if (ACTION_OPEN_UI.equals(action)) {
            Intent launch = new Intent(context, MainActivity.class);
            launch.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            context.startActivity(launch);
        } else if (ACTION_STOP_SERVER.equals(action)) {
            stopServerAndNotify(context);
        }
    }

    private void stopServerAndNotify(Context context) {
        JSONObject settings = SettingsManager.loadSettings(context);
        try {
            settings.put("server_running", false);
        } catch (Exception ignored) {}
        SettingsManager.saveSettings(context, settings);

        context.stopService(new Intent(context, ServerService.class));
        GlobalVars.started = false;
        new Handler(Looper.getMainLooper()).post(new StopToastRunnable(context));

        Intent broadcast = new Intent(ACTION_SERVER_STOPPED);
        broadcast.setPackage(context.getPackageName());
        context.sendBroadcast(broadcast);
    }
}
