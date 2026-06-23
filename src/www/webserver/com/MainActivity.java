package www.webserver.com;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.ScrollView;
import android.widget.Toast;
import androidx.documentfile.provider.DocumentFile;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.lang.ref.WeakReference;

public class MainActivity extends Activity
        implements LegacyFolderPicker.OnFolderSelectedListener,
                   SafFolderPicker.Callback {

    private static final String TAG = "WebServer";
    private static final int PERMISSION_REQUEST_NOTIFICATION = 101;
    private static final int PERMISSION_REQUEST_STORAGE = 100;
    private static final int PERMISSION_REQUEST_MEDIA = 102;
    private static final int PERMISSION_REQUEST_LOCATION = 105;
    private static final int REQUEST_IGNORE_BATTERY_OPTIMIZATIONS = 104;

    private TextView ipTextView;

    private Activity activity;
    private Context context;
    private TextView pathTextView;
    private EditText portEditText;
    private TextView statusTextView;
    private CheckBox httpsCheckBox;
    private CheckBox highSpeedCheckBox;
    private CheckBox hostHtmlCheckBox;
    private Button secureButton;
    private Button findServersButton;
    private SharedPreferences prefs;

    private JSONObject safLabelCache = new JSONObject();
    private static final String CACHE_FILE = "saf_provider_cache.json";

    private LegacyFolderPicker legacyFolderPicker;

    private static final class UiHandler extends Handler {
        private final WeakReference<MainActivity> mActivityRef;
        UiHandler(Looper looper, MainActivity activity) {
            super(looper);
            mActivityRef = new WeakReference<>(activity);
        }
        MainActivity getActivity() {
            return mActivityRef.get();
        }
    }
    private UiHandler uiHandler;

    private boolean batteryOptimizationRequested = false;
    private boolean serverStartedOnce = false;

    private boolean storagePermissionDenied = false;
    private boolean notificationPermissionDenied = false;
    private boolean mediaPermissionDenied = false;
    private boolean locationPermissionDenied = false;

    private JSONObject settingsJson = new JSONObject();
    private int savedPort = 9999;
    private String savedLegacyPath = null;
    private boolean savedHttpsEnabled = false;
    private String savedUsername = "";
    private String savedPassword = "";
    private boolean serverRunning = false;
    private boolean savedDiscoveryActive = false;

    private AlertDialog discoveryDialog;
    private DiscoveryServerAdapter discoveryAdapter;
    private boolean discoveryDialogShown = false;
    private Runnable cancelReappearRunnable = null;
    private Runnable refreshTimerRunnable;
    private static final int REFRESH_INTERVAL_MS = 1000;

    private final AtomicBoolean serverStartInProgress = new AtomicBoolean(false);

    private final BroadcastReceiver ipUpdateReceiver = new IpUpdateReceiver();
    private final BroadcastReceiver serverStopReceiver = new ServerStopReceiver();
    private final BroadcastReceiver bindResultReceiver = new BindResultReceiver();

    private class IpUpdateReceiver extends BroadcastReceiver {
        @Override public void onReceive(Context ctx, Intent intent) {
            String ip = intent.getStringExtra("ip");
            if (ip != null && !ip.isEmpty() && ipTextView != null) {
                ipTextView.setText(" " + ip);
                if (statusTextView != null) {
                    statusTextView.setTextColor(getResources().getColor(R.color.status_running));
                    statusTextView.setText(" " + getResources().getString(R.string.status_running));
                }
                GlobalVars.started = true;
                serverRunning = true;
                saveSettings();
            }
        }
    }

    private class ServerStopReceiver extends BroadcastReceiver {
        @Override public void onReceive(Context context, Intent intent) {
            runOnUiThread(() -> {
                serverRunning = ServerService.isRunning();
                GlobalVars.started = serverRunning;
                loadData();
            });
        }
    }

    private class BindResultReceiver extends BroadcastReceiver {
        @Override public void onReceive(Context context, Intent intent) {
            serverRunning = false;
            saveSettings();
            runOnUiThread(() -> {
                Toast.makeText(MainActivity.this, "Server failed to start – port may be in use.", Toast.LENGTH_LONG).show();
                Button startStopButton = (Button) findViewById(R.id.startStopButton);
                if (startStopButton != null) {
                    startStopButton.setText(R.string.start_server_button);
                    startStopButton.setEnabled(true);
                }
                httpsCheckBox.setEnabled(true);
                highSpeedCheckBox.setEnabled(true);
                hostHtmlCheckBox.setEnabled(true);
                secureButton.setEnabled(true);
                portEditText.setEnabled(true);
                pathTextView.setEnabled(true);
                findViewById(R.id.changePathButton).setEnabled(true);
                statusTextView.setText(" STOPPED");
                statusTextView.setTextColor(getResources().getColor(R.color.status_stopped));
            });
        }
    }

    private class DiscoveryServerAdapter extends ArrayAdapter<String> {
        DiscoveryServerAdapter() {
            super(MainActivity.this, android.R.layout.simple_list_item_1, new ArrayList<>());
        }
        @Override public View getView(int pos, View cv, ViewGroup parent) {
            View v = super.getView(pos, cv, parent);
            TextView tv = (TextView) v.findViewById(android.R.id.text1);
            tv.setPadding((int)(25 * getResources().getDisplayMetrics().density), 0, tv.getPaddingRight(), 0);
            tv.setTextColor(getResources().getColor(R.color.text_primary));
            return v;
        }
    }

    private class RefreshTimerTask implements Runnable {
        @Override public void run() {
            MainActivity act = uiHandler.getActivity();
            if (act != null && act.discoveryDialogShown && act.discoveryDialog != null && act.discoveryDialog.isShowing()) {
                act.refreshDiscoveryDialog();
                uiHandler.postDelayed(this, REFRESH_INTERVAL_MS);
            }
        }
    }

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.main);

        GlobalVars.appContext = getApplicationContext();
        this.activity = this;
        prefs = getSharedPreferences("webserver_prefs", MODE_PRIVATE);

        try {
            File cacheFile = new File(getFilesDir(), CACHE_FILE);
            if (cacheFile.exists()) {
                String jsonStr = readFileToString(cacheFile);
                safLabelCache = new JSONObject(jsonStr);
            }
        } catch (Exception e) { safLabelCache = new JSONObject(); }

        storagePermissionDenied = prefs.getBoolean("storage_perm_denied", false);
        notificationPermissionDenied = prefs.getBoolean("notification_perm_denied", false);
        mediaPermissionDenied = prefs.getBoolean("media_perm_denied", false);
        locationPermissionDenied = prefs.getBoolean("location_perm_denied", false);

        this.ipTextView = (TextView) findViewById(R.id.ipAddress);
        this.portEditText = (EditText) findViewById(R.id.port);
        this.statusTextView = (TextView) findViewById(R.id.status);
        this.pathTextView = (TextView) findViewById(R.id.path);
        this.httpsCheckBox = (CheckBox) findViewById(R.id.httpsCheckBox);
        this.highSpeedCheckBox = (CheckBox) findViewById(R.id.highSpeedCheckBox);
        this.hostHtmlCheckBox = (CheckBox) findViewById(R.id.hostHtmlCheckBox);
        this.secureButton = (Button) findViewById(R.id.secureButton);
        this.findServersButton = (Button) findViewById(R.id.findServersButton);

        updateStatusBar();
        this.portEditText.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.text_primary)));

        KeystoreProvider.ensureKeystore(this);

        loadSettings();

        portEditText.setText(String.valueOf(savedPort));
        httpsCheckBox.setChecked(savedHttpsEnabled);
        highSpeedCheckBox.setChecked(settingsJson.optBoolean("high_speed", false));
        hostHtmlCheckBox.setChecked(settingsJson.optBoolean("host_html", false));
        GlobalVars.highSpeedMode = highSpeedCheckBox.isChecked();
        GlobalVars.hostHtmlEnabled = hostHtmlCheckBox.isChecked();

        String savedMode = settingsJson.optString("mode", "normal");
        if ("saf".equals(savedMode)) {
            String uriStr = settingsJson.optString("root_uri", "");
            if (!uriStr.isEmpty()) {
                GlobalVars.rootUri = Uri.parse(uriStr);
                GlobalVars.legacyPath = null;
            }
        } else {
            if (savedLegacyPath != null && !savedLegacyPath.isEmpty()) {
                GlobalVars.legacyPath = savedLegacyPath;
                GlobalVars.rootUri = null;
            } else {
                String defaultPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/";
                GlobalVars.legacyPath = defaultPath;
                savedLegacyPath = defaultPath;
                saveSettings();
            }
        }
        updatePathDisplay();

        loadData();

        if (!hasStoragePermission() && !storagePermissionDenied) requestStoragePermission();

        serverStartedOnce = prefs.getBoolean("server_started_once", false);
        batteryOptimizationRequested = prefs.getBoolean("battery_optimization_requested", false);
        if (serverStartedOnce && !batteryOptimizationRequested && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            requestBatteryOptimization();

        this.portEditText.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                String portStr = portEditText.getText().toString().trim();
                if (!portStr.isEmpty()) {
                    try {
                        int port = Integer.parseInt(portStr);
                        if (port >= 1 && port <= 65535) { savedPort = port; GlobalVars.port = port; saveSettings(); }
                    } catch (NumberFormatException ignored) {}
                }
            }
        });

        this.httpsCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            savedHttpsEnabled = isChecked; GlobalVars.httpsEnabled = isChecked; saveSettings();
        });

        this.highSpeedCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            GlobalVars.highSpeedMode = isChecked;
            try { settingsJson.put("high_speed", isChecked); } catch (JSONException e) {}
            SettingsManager.saveSettings(this, settingsJson);
        });

        this.hostHtmlCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            GlobalVars.hostHtmlEnabled = isChecked;
            try { settingsJson.put("host_html", isChecked); } catch (JSONException e) {}
            SettingsManager.saveSettings(this, settingsJson);
        });

        IntentFilter ipFilter = new IntentFilter("www.webserver.com.IP_UPDATED");
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(ipUpdateReceiver, ipFilter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(ipUpdateReceiver, ipFilter);

        IntentFilter stopFilter = new IntentFilter(NotificationActionReceiver.ACTION_SERVER_STOPPED);
        stopFilter.addAction(ServerService.ACTION_SERVER_STOPPED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(serverStopReceiver, stopFilter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(serverStopReceiver, stopFilter);

        IntentFilter bindFilter = new IntentFilter(ServerService.ACTION_BIND_FAILED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(bindResultReceiver, bindFilter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(bindResultReceiver, bindFilter);

        uiHandler = new UiHandler(Looper.getMainLooper(), this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean serviceAlive = ServerService.isRunning();
        GlobalVars.started = serviceAlive;
        loadSettings();
        loadData();
        if (serverRunning && !serviceAlive && !serverStartInProgress.get()) {
            performServerStart();
        }

        if (savedDiscoveryActive && !DiscoveryService.isActive()) {
            DiscoveryService.start(this);
            findServersButton.setText("Stop Discovery");
        } else {
            syncDiscoveryButton();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.help_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_help) {
            HelpDialogHelper.show(this);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private String readFileToString(File file) throws IOException {
        FileInputStream fis = new FileInputStream(file);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int len;
        while ((len = fis.read(buf)) != -1) bos.write(buf, 0, len);
        fis.close();
        return bos.toString("UTF-8");
    }

    @Override
    public void onFolderSelected(String absolutePath) {
        savedLegacyPath = absolutePath;
        GlobalVars.legacyPath = absolutePath;
        GlobalVars.rootUri = null;
        try { settingsJson.put("mode", "normal"); } catch (JSONException ignored) {}
        saveSettings();
        updatePathDisplay();
    }

    @Override
    public void onSafFolderSelected(Uri treeUri) {
        try {
            getContentResolver().takePersistableUriPermission(treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (SecurityException e) {
            Toast.makeText(this, "Failed to get persistent permission.", Toast.LENGTH_SHORT).show();
            return;
        }
        GlobalVars.rootUri = treeUri;
        GlobalVars.legacyPath = null;
        savedLegacyPath = null;
        try { settingsJson.put("mode", "saf"); settingsJson.put("root_uri", treeUri.toString()); } catch (JSONException ignored) {}
        saveSettings();
        updatePathDisplay();
        Toast.makeText(this, "Folder selected via SAF", Toast.LENGTH_SHORT).show();
    }

    private void showLegacyPicker() {
        legacyFolderPicker = new LegacyFolderPicker(this, this);
        legacyFolderPicker.show();
    }

    private void openSafPicker() {
        SafFolderPicker.open(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        SafFolderPicker.handleResult(requestCode, resultCode, data, this);
    }

    private void loadSettings() {
        settingsJson = SettingsManager.loadSettings(this);
        savedPort = settingsJson.optInt("port", 9999);
        savedHttpsEnabled = settingsJson.optBoolean("https_enabled", false);
        savedLegacyPath = settingsJson.optString("storage_path", "");
        if (savedLegacyPath.isEmpty()) savedLegacyPath = null;
        savedUsername = settingsJson.optString("username", "");
        savedPassword = settingsJson.optString("password", "");
        serverRunning = settingsJson.optBoolean("server_running", false);
        savedDiscoveryActive = settingsJson.optBoolean("discovery_active", false);
        GlobalVars.highSpeedMode = settingsJson.optBoolean("high_speed", false);
        GlobalVars.hostHtmlEnabled = settingsJson.optBoolean("host_html", false);

        GlobalVars.port = savedPort;
        GlobalVars.httpsEnabled = savedHttpsEnabled;
        if (savedLegacyPath != null) {
            GlobalVars.legacyPath = savedLegacyPath;
            GlobalVars.rootUri = null;
        }
        AuthManager.loadFromJson(settingsJson);
    }

    private void saveSettings() {
        try {
            settingsJson.put("port", savedPort);
            settingsJson.put("ip", GlobalVars.ip != null ? GlobalVars.ip : "");
            settingsJson.put("https_enabled", savedHttpsEnabled);
            settingsJson.put("storage_path", savedLegacyPath != null ? savedLegacyPath : "");
            settingsJson.put("server_running", serverRunning);
            settingsJson.put("username", savedUsername);
            settingsJson.put("password", savedPassword);
            settingsJson.put("discovery_active", DiscoveryService.isActive());
            settingsJson.put("high_speed", GlobalVars.highSpeedMode);
            settingsJson.put("host_html", GlobalVars.hostHtmlEnabled);
            SettingsManager.saveSettings(this, settingsJson);
        } catch (JSONException e) { e.printStackTrace(); }
    }

    private void updateStatusBar() {
        if (Build.VERSION.SDK_INT >= 23) {
            Window window = getWindow();
            boolean isNight = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                    == Configuration.UI_MODE_NIGHT_YES;
            int flags = window.getDecorView().getSystemUiVisibility();
            if (isNight) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            else flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            window.getDecorView().setSystemUiVisibility(flags);
        }
    }

    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateStatusBar();
        loadData();
        if (portEditText != null)
            portEditText.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.text_primary)));
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(ipUpdateReceiver); } catch (Exception ignored) {}
        try { unregisterReceiver(serverStopReceiver); } catch (Exception ignored) {}
        try { unregisterReceiver(bindResultReceiver); } catch (Exception ignored) {}
        if (discoveryDialog != null && discoveryDialog.isShowing()) discoveryDialog.dismiss();
        if (legacyFolderPicker != null) legacyFolderPicker.dismiss();
        stopRefreshTimer();
        if (cancelReappearRunnable != null) {
            uiHandler.removeCallbacks(cancelReappearRunnable);
            cancelReappearRunnable = null;
        }
        saveSettings();
    }

    private boolean hasStoragePermission() {
        if (GlobalVars.rootUri != null) return true;
        if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
        return Build.VERSION.SDK_INT < 23 || checkSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE") == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasNotificationPermission() {
        return Build.VERSION.SDK_INT < 33 || checkSelfPermission("android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasMediaPermissions() {
        if (Build.VERSION.SDK_INT >= 33)
            return checkSelfPermission("android.permission.READ_MEDIA_IMAGES") == PackageManager.PERMISSION_GRANTED &&
                   checkSelfPermission("android.permission.READ_MEDIA_VIDEO") == PackageManager.PERMISSION_GRANTED &&
                   checkSelfPermission("android.permission.READ_MEDIA_AUDIO") == PackageManager.PERMISSION_GRANTED;
        return true;
    }

    private boolean hasLocationPermissionForDiscovery() {
        if (Build.VERSION.SDK_INT >= 31) {
            if (checkSelfPermission("android.permission.NEARBY_WIFI_DEVICES") == PackageManager.PERMISSION_GRANTED) return true;
            return checkSelfPermission("android.permission.ACCESS_COARSE_LOCATION") == PackageManager.PERMISSION_GRANTED ||
                   checkSelfPermission("android.permission.ACCESS_FINE_LOCATION") == PackageManager.PERMISSION_GRANTED;
        } else if (Build.VERSION.SDK_INT >= 23)
            return checkSelfPermission("android.permission.ACCESS_COARSE_LOCATION") == PackageManager.PERMISSION_GRANTED ||
                   checkSelfPermission("android.permission.ACCESS_FINE_LOCATION") == PackageManager.PERMISSION_GRANTED;
        return true;
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= 30) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            return;
        }
        if (Build.VERSION.SDK_INT >= 23)
            requestPermissions(new String[]{"android.permission.WRITE_EXTERNAL_STORAGE"}, PERMISSION_REQUEST_STORAGE);
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33)
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, PERMISSION_REQUEST_NOTIFICATION);
    }

    private void requestMediaPermissions() {
        if (Build.VERSION.SDK_INT >= 33)
            requestPermissions(new String[]{
                "android.permission.READ_MEDIA_IMAGES",
                "android.permission.READ_MEDIA_VIDEO",
                "android.permission.READ_MEDIA_AUDIO"
            }, PERMISSION_REQUEST_MEDIA);
    }

    private void requestLocationPermission() {
        if (Build.VERSION.SDK_INT >= 31)
            requestPermissions(new String[]{"android.permission.NEARBY_WIFI_DEVICES"}, PERMISSION_REQUEST_LOCATION);
        else if (Build.VERSION.SDK_INT >= 23)
            requestPermissions(new String[]{"android.permission.ACCESS_COARSE_LOCATION"}, PERMISSION_REQUEST_LOCATION);
    }

    private void requestBatteryOptimization() {
        if (batteryOptimizationRequested) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && pm.isIgnoringBatteryOptimizations(getPackageName())) {
                batteryOptimizationRequested = true;
                prefs.edit().putBoolean("battery_optimization_requested", true).commit();
                return;
            }
        }

        batteryOptimizationRequested = true;
        prefs.edit().putBoolean("battery_optimization_requested", true).commit();

        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        try {
            startActivity(intent);
        } catch (Exception ignored) {}
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                storagePermissionDenied = false;
                prefs.edit().putBoolean("storage_perm_denied", false).apply();
            } else {
                storagePermissionDenied = true;
                prefs.edit().putBoolean("storage_perm_denied", true).apply();
            }
        } else if (requestCode == PERMISSION_REQUEST_NOTIFICATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                notificationPermissionDenied = false;
                prefs.edit().putBoolean("notification_perm_denied", false).apply();
            } else {
                notificationPermissionDenied = true;
                prefs.edit().putBoolean("notification_perm_denied", true).apply();
            }
        } else if (requestCode == PERMISSION_REQUEST_MEDIA) {
            boolean allGranted = grantResults.length == 3 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                grantResults[1] == PackageManager.PERMISSION_GRANTED &&
                grantResults[2] == PackageManager.PERMISSION_GRANTED;
            if (allGranted) {
                mediaPermissionDenied = false;
                prefs.edit().putBoolean("media_perm_denied", false).apply();
                showPickerChoiceDialog();
            } else {
                mediaPermissionDenied = true;
                prefs.edit().putBoolean("media_perm_denied", true).apply();
            }
        } else if (requestCode == PERMISSION_REQUEST_LOCATION) {
            locationPermissionDenied = (grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED);
            prefs.edit().putBoolean("location_perm_denied", locationPermissionDenied).apply();
        }
    }

    public void onStartServerClick() {
        final String portStr = portEditText.getText().toString().trim();
        final String defaultPort = String.valueOf(savedPort > 0 ? savedPort : 9999);

        new Thread(() -> {
            final WeakReference<MainActivity> activityRef = new WeakReference<>(MainActivity.this);
            boolean needDefault = portStr.isEmpty() && !RootChecker.isDeviceRooted();
            runOnUiThread(() -> {
                MainActivity act = activityRef.get();
                if (act == null || act.isFinishing() || act.isDestroyed()) return;
                final String effectivePort = needDefault ? defaultPort : portStr;

                if (!effectivePort.isEmpty()) {
                    try {
                        int port = Integer.parseInt(effectivePort);
                        if (port >= 1 && port <= 65535) {
                            savedPort = port;
                            GlobalVars.port = port;
                        } else {
                            Toast.makeText(act, R.string.error_invalid_port, Toast.LENGTH_LONG).show();
                            return;
                        }
                    } catch (NumberFormatException e) {
                        Toast.makeText(act, R.string.error_invalid_port, Toast.LENGTH_LONG).show();
                        return;
                    }
                }

                if (!hasStoragePermission()) {
                    if (!storagePermissionDenied) requestStoragePermission();
                    else Toast.makeText(act, "Storage permission is required.", Toast.LENGTH_LONG).show();
                    return;
                }

                if (Build.VERSION.SDK_INT >= 33 && !hasNotificationPermission()) {
                    if (!notificationPermissionDenied) requestNotificationPermission();
                    else Toast.makeText(act, "Notification permission is required.", Toast.LENGTH_LONG).show();
                    return;
                }

                if (saveAll()) {
                    if (GlobalVars.rootUri == null && (GlobalVars.legacyPath == null || !new File(GlobalVars.legacyPath).exists())) {
                        Toast.makeText(getBaseContext(), "Please select a folder first.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (serverStartInProgress.compareAndSet(false, true)) {
                        performServerStart();
                    }
                }
            });
        }).start();
    }

    private void performServerStart() {
        Button startStopButton = (Button) findViewById(R.id.startStopButton);
        startStopButton.setEnabled(false);
        startStopButton.setText("Starting…");

        new Thread(() -> {
            boolean valid = true;
            if (httpsCheckBox.isChecked()) {
                try {
                    KeystoreProvider.getKeyStore(MainActivity.this);
                } catch (Exception e) { valid = false; }
            }
            final boolean keystoreValid = valid;
            runOnUiThread(() -> {
                if (!keystoreValid) {
                    Toast.makeText(MainActivity.this, "HTTPS keystore missing.", Toast.LENGTH_LONG).show();
                    startStopButton.setText(R.string.start_server_button);
                    startStopButton.setEnabled(true);
                    serverStartInProgress.set(false);
                    return;
                }
                continueServerStart(startStopButton);
            });
        }).start();
    }

    private void continueServerStart(Button startStopButton) {
        serverRunning = true;
        saveSettings();

        startStopButton.setEnabled(true);
        startStopButton.setText(R.string.stop_server_button);
        ipTextView.setText(" Detecting…");

        stopService(new Intent(this, ServerService.class));
        clearAppInternalData();

        GlobalVars.port = savedPort;
        GlobalVars.rootUri = GlobalVars.rootUri;
        GlobalVars.legacyPath = savedLegacyPath;
        GlobalVars.httpsEnabled = savedHttpsEnabled;

        try {
            if (GlobalVars.port < 1024)
                Toast.makeText(getBaseContext(), R.string.port_below_1024_warning, Toast.LENGTH_LONG).show();
            statusTextView.setTextColor(getResources().getColor(R.color.status_running));
            statusTextView.setText(" " + getResources().getString(R.string.status_running));
            pathTextView.setEnabled(false);
            pathTextView.setTextColor(getResources().getColor(R.color.text_secondary));
            portEditText.setEnabled(false);
            portEditText.setTextColor(getResources().getColor(R.color.text_secondary));
            findViewById(R.id.changePathButton).setEnabled(false);
            httpsCheckBox.setEnabled(false);
            httpsCheckBox.setTextColor(getResources().getColor(R.color.text_secondary));
            highSpeedCheckBox.setEnabled(false);
            highSpeedCheckBox.setTextColor(getResources().getColor(R.color.text_secondary));
            hostHtmlCheckBox.setEnabled(false);
            hostHtmlCheckBox.setTextColor(getResources().getColor(R.color.text_secondary));
            secureButton.setEnabled(false);
            startService(new Intent(MainActivity.this, ServerService.class));
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(MainActivity.this, "Error starting server: " + e.getMessage(), Toast.LENGTH_LONG).show();
            startStopButton.setText(R.string.start_server_button);
            secureButton.setEnabled(true);
            httpsCheckBox.setEnabled(true);
            highSpeedCheckBox.setEnabled(true);
            hostHtmlCheckBox.setEnabled(true);
        } finally {
            serverStartInProgress.set(false);
        }
    }

    private void clearAppInternalData() {
        GlobalVars.started = false;
        GlobalVars.ip = null;
    }

    public void onStopServerClick() {
        serverRunning = false;
        saveSettings();
        GlobalVars.started = false;
        serverStartedOnce = true;
        prefs.edit().putBoolean("server_started_once", true).apply();

        TextView tvStatus = (TextView) findViewById(R.id.status);
        TextView tvIp = (TextView) findViewById(R.id.ipAddress);
        TextView tvPath = (TextView) findViewById(R.id.path);
        EditText etPort = (EditText) findViewById(R.id.port);
        Button btnStartStop = (Button) findViewById(R.id.startStopButton);
        Button btnChange = (Button) findViewById(R.id.changePathButton);

        tvStatus.setTextColor(getResources().getColor(R.color.status_stopped));
        tvStatus.setText(" " + getResources().getString(R.string.status_stopped));
        tvIp.setText(" 0.0.0.0");
        tvPath.setEnabled(true);
        tvPath.setTextColor(getResources().getColor(R.color.text_primary));
        etPort.setEnabled(true);
        etPort.setTextColor(getResources().getColor(R.color.text_primary));
        btnChange.setEnabled(true);
        httpsCheckBox.setEnabled(true);
        httpsCheckBox.setTextColor(getResources().getColor(R.color.text_primary));
        highSpeedCheckBox.setEnabled(true);
        highSpeedCheckBox.setTextColor(getResources().getColor(R.color.text_primary));
        hostHtmlCheckBox.setEnabled(true);
        hostHtmlCheckBox.setTextColor(getResources().getColor(R.color.text_primary));
        secureButton.setEnabled(true);
        btnStartStop.setText(R.string.start_server_button);

        stopService(new Intent(this, ServerService.class));
        GlobalVars.ip = "0.0.0.0";
        serverStartInProgress.set(false);
    }

    public boolean saveAll() {
        if (portEditText.getText().length() > 0) {
            try {
                int i = Integer.parseInt(portEditText.getText().toString());
                if (i >= 1 && i <= 65535) {
                    saveAllSettings();
                    return true;
                }
                Toast.makeText(this, getResources().getString(R.string.error_invalid_port), Toast.LENGTH_LONG).show();
                return false;
            } catch (NumberFormatException e) {
                Toast.makeText(this, R.string.error_invalid_port, Toast.LENGTH_LONG).show();
                return false;
            }
        }
        saveAllSettings();
        return true;
    }

    public void saveAllSettings() { saveSettings(); }

    public void myClickHandler(View view) {
        int id = view.getId();
        if (id == R.id.changePathButton) {
            if (!hasStoragePermission()) {
                if (!storagePermissionDenied) requestStoragePermission();
                else Toast.makeText(this, "Storage permission required.", Toast.LENGTH_LONG).show();
                return;
            }
            if (!hasMediaPermissions()) {
                if (!mediaPermissionDenied) requestMediaPermissions();
                else Toast.makeText(this, "Media permissions allow serving photos/videos.", Toast.LENGTH_LONG).show();
                return;
            }
            showPickerChoiceDialog();
        } else if (id == R.id.startStopButton) {
            String btnText = ((Button) findViewById(R.id.startStopButton)).getText().toString();
            if (btnText.equals(getResources().getString(R.string.start_server_button)))
                onStartServerClick();
            else
                onStopServerClick();
        }
    }

    public void onSecureClick(View view) {
        if (!hasStoragePermission()) {
            if (!storagePermissionDenied) requestStoragePermission();
            else Toast.makeText(this, "Storage permission required.", Toast.LENGTH_LONG).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(18 * getResources().getDisplayMetrics().density);
        layout.setPadding((int)(24 * getResources().getDisplayMetrics().density),
                         (int)(24 * getResources().getDisplayMetrics().density),
                         (int)(24 * getResources().getDisplayMetrics().density),
                         (int)(6 * getResources().getDisplayMetrics().density));

        TextView titleView = new TextView(this);
        titleView.setText("Set login credentials");
        titleView.setTextSize(18);
        titleView.setTextColor(getResources().getColor(R.color.text_primary));
        titleView.setPadding(0, 0, 0, pad/4);
        layout.addView(titleView);

        TextView desc = new TextView(this);
        desc.setText("Leave blank to remove protection.");
        desc.setTextSize(14);
        desc.setTextColor(getResources().getColor(R.color.text_secondary));
        desc.setPadding(0, 0, (int)(24 * getResources().getDisplayMetrics().density), pad/3);
        layout.addView(desc);

        EditText userEdit = new EditText(this);
        userEdit.setHint("Username");
        userEdit.setText(savedUsername);
        userEdit.setTextColor(getResources().getColor(R.color.text_primary));
        userEdit.setHintTextColor(getResources().getColor(R.color.text_secondary));
        userEdit.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.text_primary)));
        LinearLayout.LayoutParams userParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        userParams.bottomMargin = pad / 10;
        userEdit.setLayoutParams(userParams);
        layout.addView(userEdit);

        EditText passEdit = new EditText(this);
        passEdit.setHint("Password");
        passEdit.setText(savedPassword);
        passEdit.setTextColor(getResources().getColor(R.color.text_primary));
        passEdit.setHintTextColor(getResources().getColor(R.color.text_secondary));
        passEdit.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.text_primary)));
        LinearLayout.LayoutParams passParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        passParams.bottomMargin = pad / 10;
        passEdit.setLayoutParams(passParams);
        layout.addView(passEdit);

        builder.setView(layout);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String user = userEdit.getText().toString().trim();
            String pass = passEdit.getText().toString().trim();
            if ((!user.isEmpty() && pass.isEmpty()) || (user.isEmpty() && !pass.isEmpty())) {
                Toast.makeText(MainActivity.this, "Both username and password are required.", Toast.LENGTH_LONG).show();
                return;
            }
            savedUsername = user;
            savedPassword = pass;
            AuthManager.saveCredentials(user, pass);
            saveSettings();
            String msg = (user.isEmpty() && pass.isEmpty()) ? "Protection removed." : "Credentials updated – server locked.";
            Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("Cancel", null);

        AlertDialog d = builder.show();
        applyDialogColors(d);
    }

    private void showPickerChoiceDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        TextView titleView = new TextView(this);
        titleView.setText("Select Folder Picker");
        titleView.setTextSize(18);
        titleView.setTextColor(getResources().getColor(R.color.text_primary));
        titleView.setGravity(Gravity.CENTER);
        int pad = (int)(10 * getResources().getDisplayMetrics().density);
        int margin = (int)(21 * getResources().getDisplayMetrics().density);
        titleView.setPadding(0, (int)(21 * getResources().getDisplayMetrics().density), 0, (int)(12 * getResources().getDisplayMetrics().density));
        builder.setCustomTitle(titleView);

        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(margin, 0, margin, pad);

        boolean isNight = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        int btnBg = isNight ? 0xFFFFFFFF : 0xFF000000;
        int btnText = isNight ? 0xFF000000 : 0xFFFFFFFF;

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER);
        Button btnSAF = new Button(this); btnSAF.setText("SAF"); btnSAF.setTextSize(14); btnSAF.setTextColor(btnText);
        android.graphics.drawable.GradientDrawable gdSAF = new android.graphics.drawable.GradientDrawable();
        gdSAF.setColor(btnBg); gdSAF.setCornerRadius(8 * getResources().getDisplayMetrics().density); btnSAF.setBackground(gdSAF);
        Button btnLegacy = new Button(this); btnLegacy.setText("LEGACY"); btnLegacy.setTextSize(14); btnLegacy.setTextColor(btnText);
        android.graphics.drawable.GradientDrawable gdLegacy = new android.graphics.drawable.GradientDrawable();
        gdLegacy.setColor(btnBg); gdLegacy.setCornerRadius(8 * getResources().getDisplayMetrics().density); btnLegacy.setBackground(gdLegacy);
        LinearLayout.LayoutParams lpSAF = new LinearLayout.LayoutParams(0, (int)(42 * getResources().getDisplayMetrics().density), 1.0f);
        lpSAF.rightMargin = (int)(8 * getResources().getDisplayMetrics().density);
        row1.addView(btnSAF, lpSAF);
        LinearLayout.LayoutParams lpLegacy = new LinearLayout.LayoutParams(0, (int)(42 * getResources().getDisplayMetrics().density), 1.0f);
        lpLegacy.setMargins((int)(8 * getResources().getDisplayMetrics().density), 0, 0, 0);
        row1.addView(btnLegacy, lpLegacy);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams row2Lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        row2Lp.setMargins(0, (int)(13 * getResources().getDisplayMetrics().density), 0, (int)(11 * getResources().getDisplayMetrics().density));
        row2.setPadding(0, 0, 0, 0);
        Button btnCancel = new Button(this); btnCancel.setText("CANCEL"); btnCancel.setTextSize(14); btnCancel.setTextColor(btnText);
        android.graphics.drawable.GradientDrawable gdCancel = new android.graphics.drawable.GradientDrawable();
        gdCancel.setColor(btnBg); gdCancel.setCornerRadius(8 * getResources().getDisplayMetrics().density); btnCancel.setBackground(gdCancel);
        row2.addView(btnCancel, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, (int)(42 * getResources().getDisplayMetrics().density)));

        main.addView(row1);
        main.addView(row2, row2Lp);
        builder.setView(main);
        AlertDialog dlg = builder.create();
        btnSAF.setOnClickListener(v -> { dlg.dismiss(); openSafPicker(); });
        btnLegacy.setOnClickListener(v -> { dlg.dismiss(); showLegacyPicker(); });
        btnCancel.setOnClickListener(v -> dlg.dismiss());
        dlg.setOnShowListener(d -> { applyDialogColors(dlg); btnSAF.setTextColor(btnText); btnLegacy.setTextColor(btnText); btnCancel.setTextColor(btnText); });
        dlg.show();
    }

    private String[] getLegacyDisplayInfo(String fullPath) {
        if (fullPath == null) return new String[]{"Unknown", ""};
        String cleanPath = fullPath;
        if (cleanPath.endsWith("/") && cleanPath.length() > 1) cleanPath = cleanPath.substring(0, cleanPath.length() - 1);
        if (cleanPath.equals("/")) return new String[]{"Root", ""};
        else if (cleanPath.startsWith("/storage/emulated")) {
            String base = "/storage/emulated";
            String rest = cleanPath.substring(base.length());
            if (rest.matches("/\\d+.*")) {
                rest = rest.replaceFirst("/\\d+", "");
                if (rest.isEmpty()) return new String[]{"Internal Storage", ""};
                if (rest.startsWith("/")) rest = rest.substring(1);
                return new String[]{"Internal Storage", rest};
            }
            return new String[]{"Internal Storage", rest.startsWith("/") ? rest.substring(1) : rest};
        } else if (cleanPath.startsWith("/storage/")) {
            String after = cleanPath.substring("/storage/".length());
            int slash = after.indexOf("/");
            if (slash != -1) {
                String volName = after.substring(0, slash);
                String type = volName.toLowerCase().contains("usb") ? "USB" : "SD CARD";
                String rel = after.substring(slash + 1);
                return new String[]{type, rel};
            } else return new String[]{after, ""};
        } else if (cleanPath.startsWith("/mnt/media_rw/")) {
            String after = cleanPath.substring("/mnt/media_rw/".length());
            int slash = after.indexOf("/");
            if (slash != -1) {
                String volName = after.substring(0, slash);
                String type = volName.toLowerCase().contains("usb") ? "USB" : "SD CARD";
                String rel = after.substring(slash + 1);
                return new String[]{type, rel};
            } else return new String[]{after, ""};
        } else {
            return new String[]{cleanPath, ""};
        }
    }

    private void updatePathDisplay() {
        if (GlobalVars.rootUri != null) {
            String[] info = getSafDisplayInfo(GlobalVars.rootUri);
            String label = info[0];
            String rel = info[1];
            if (rel.isEmpty()) pathTextView.setText(label);
            else {
                if (!rel.startsWith("/")) rel = "/" + rel;
                pathTextView.setText(label + rel);
            }
        } else if (GlobalVars.legacyPath != null) {
            String[] info = getLegacyDisplayInfo(GlobalVars.legacyPath);
            String label = info[0];
            String rel = info[1];
            if (rel.isEmpty()) pathTextView.setText(label);
            else {
                if (!rel.startsWith("/")) rel = "/" + rel;
                pathTextView.setText(label + rel);
            }
        } else {
            pathTextView.setText("No folder selected");
        }
    }

    private String getSafProviderLabel(Uri treeUri) {
        if (treeUri == null) return "SAF";
        String authority = treeUri.getAuthority();
        if (authority == null) return "SAF";
        String cached = safLabelCache.optString(authority, null);
        if (cached != null && !cached.equals("SAF")) return cached;
        String label = null;
        try {
            DocumentFile rootDir = DocumentFile.fromTreeUri(this, treeUri);
            if (rootDir != null) {
                String name = rootDir.getName();
                if (name != null && !name.isEmpty()) label = name;
            }
        } catch (Exception ignored) {}
        if (label == null) {
            Cursor cursor = null;
            try {
                Uri rootsUri = DocumentsContract.buildRootsUri(authority);
                cursor = getContentResolver().query(rootsUri,
                    new String[]{DocumentsContract.Root.COLUMN_TITLE}, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(DocumentsContract.Root.COLUMN_TITLE);
                    if (idx >= 0) label = cursor.getString(idx);
                }
            } catch (Exception ignored) {
            } finally {
                if (cursor != null) { try { cursor.close(); } catch (Exception ignored) {} }
            }
        }
        if (label != null && !label.isEmpty()) {
            try { safLabelCache.put(authority, label); saveLabelCache(); } catch (JSONException ignored) {}
            return label;
        }
        return "SAF";
    }

    private void saveLabelCache() {
        try {
            File cacheFile = new File(getFilesDir(), CACHE_FILE);
            try (FileOutputStream fos = new FileOutputStream(cacheFile)) {
                fos.write(safLabelCache.toString().getBytes("UTF-8"));
            }
        } catch (Exception ignored) {}
    }

    private String[] getSafDisplayInfo(Uri treeUri) {
        if (treeUri == null) return new String[]{"SAF", ""};
        try {
            String path = getPathFromTreeUri(treeUri);
            if (path != null && !path.isEmpty()) return getLegacyDisplayInfo(path);
        } catch (Exception e) {}
        if (Build.VERSION.SDK_INT >= 24) {
            try {
                StorageManager sm = (StorageManager) getSystemService(Context.STORAGE_SERVICE);
                if (sm != null) {
                    StorageVolume volume = sm.getStorageVolume(treeUri);
                    if (volume != null) {
                        String desc = volume.getDescription(this);
                        if (desc != null) {
                            String relPath = extractRelativePath(treeUri);
                            return relPath.isEmpty() ? new String[]{desc, ""} : new String[]{desc, relPath};
                        }
                    }
                }
            } catch (Exception e) {}
        }
        String appLabel = getSafProviderLabel(treeUri);
        String relPath = extractRelativePath(treeUri);
        if (relPath.isEmpty()) return new String[]{appLabel, ""};
        else return new String[]{appLabel, relPath};
    }

    private String extractRelativePath(Uri treeUri) {
        try {
            String docId = DocumentsContract.getTreeDocumentId(treeUri);
            if (docId == null) return "";
            int colon = docId.indexOf(':');
            if (colon >= 0) return docId.substring(colon + 1);
            else return docId;
        } catch (Exception e) { return ""; }
    }

    private String getPathFromTreeUri(Uri treeUri) {
        if (Build.VERSION.SDK_INT >= 21) {
            String docId = DocumentsContract.getTreeDocumentId(treeUri);
            if (docId != null) {
                String[] parts = docId.split(":");
                if (parts.length == 2) {
                    String type = parts[0];
                    String id = parts[1];
                    if ("primary".equalsIgnoreCase(type))
                        return Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + id;
                    else {
                        File[] externalDirs = getExternalFilesDirs(null);
                        for (File dir : externalDirs) {
                            if (dir != null && dir.getAbsolutePath().contains(type)) {
                                String base = dir.getAbsolutePath().split("/Android/data")[0];
                                return base + "/" + id;
                            }
                        }
                        return "/storage/" + type + "/" + id;
                    }
                }
            }
        }
        return null;
    }

    public void loadData() {
        Button button = (Button) findViewById(R.id.startStopButton);
        Button button2 = (Button) findViewById(R.id.changePathButton);

        boolean actuallyRunning = ServerService.isRunning();
        GlobalVars.started = actuallyRunning;

        if (actuallyRunning) {
            statusTextView.setTextColor(getResources().getColor(R.color.status_running));
            statusTextView.setText(" " + getResources().getString(R.string.status_running));
            ipTextView.setText(" " + (GlobalVars.ip != null ? GlobalVars.ip : "Detecting…"));
            portEditText.setEnabled(false);
            portEditText.setTextColor(getResources().getColor(R.color.text_secondary));
            button2.setEnabled(false);
            pathTextView.setEnabled(false);
            pathTextView.setTextColor(getResources().getColor(R.color.text_secondary));
            httpsCheckBox.setEnabled(false);
            httpsCheckBox.setTextColor(getResources().getColor(R.color.text_secondary));
            highSpeedCheckBox.setEnabled(false);
            highSpeedCheckBox.setTextColor(getResources().getColor(R.color.text_secondary));
            hostHtmlCheckBox.setEnabled(false);
            hostHtmlCheckBox.setTextColor(getResources().getColor(R.color.text_secondary));
            secureButton.setEnabled(false);
            button.setText(R.string.stop_server_button);
        } else {
            statusTextView.setTextColor(getResources().getColor(R.color.status_stopped));
            statusTextView.setText(" " + getResources().getString(R.string.status_stopped));
            ipTextView.setText(" 0.0.0.0");
            button.setText(R.string.start_server_button);
            httpsCheckBox.setEnabled(true);
            httpsCheckBox.setTextColor(getResources().getColor(R.color.text_primary));
            highSpeedCheckBox.setEnabled(true);
            highSpeedCheckBox.setTextColor(getResources().getColor(R.color.text_primary));
            hostHtmlCheckBox.setEnabled(true);
            hostHtmlCheckBox.setTextColor(getResources().getColor(R.color.text_primary));
            secureButton.setEnabled(true);
            portEditText.setEnabled(true);
            portEditText.setTextColor(getResources().getColor(R.color.text_primary));
            pathTextView.setEnabled(true);
            pathTextView.setTextColor(getResources().getColor(R.color.text_primary));
            button2.setEnabled(true);
        }

        syncDiscoveryButton();
    }

    private void syncDiscoveryButton() {
        if (findServersButton == null) return;
        findServersButton.setText(DiscoveryService.isActive() ? "Stop Discovery" : "Find Servers");
    }

    public void onFindServersClick(View view) {
        if (DiscoveryService.isActive()) {
            DiscoveryService.stop(this);
            findServersButton.setText("Find Servers");
            savedDiscoveryActive = false;
            saveSettings();
            if (cancelReappearRunnable != null) {
                uiHandler.removeCallbacks(cancelReappearRunnable);
                cancelReappearRunnable = null;
            }
            if (discoveryDialog != null && discoveryDialog.isShowing()) discoveryDialog.dismiss();
            Toast.makeText(this, "Discovery stopped.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 && !hasNotificationPermission()) {
            if (!notificationPermissionDenied) {
                requestNotificationPermission();
            } else {
                Toast.makeText(this, "Notification permission is required for discovery.", Toast.LENGTH_LONG).show();
            }
            return;
        }
        DiscoveryService.start(this);
        savedDiscoveryActive = true;
        saveSettings();
        showDiscoveryDialog();
    }

    private void showDiscoveryDialog() {
        if (discoveryDialog != null && discoveryDialog.isShowing()) {
            discoveryDialog.dismiss();
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Found Servers");
        discoveryAdapter = new DiscoveryServerAdapter();
        builder.setAdapter(discoveryAdapter, null);
        builder.setNegativeButton("Cancel", (d, w) -> {
            if (discoveryDialog != null && discoveryDialog.isShowing()) discoveryDialog.dismiss();
            discoveryDialogShown = false;
        });
        builder.setCancelable(false);
        discoveryDialog = builder.create();
        discoveryDialog.setOnDismissListener(d -> {
            if (!DiscoveryService.isActive()) {
                discoveryDialog = null;
                discoveryAdapter = null;
                discoveryDialogShown = false;
                stopRefreshTimer();
            }
        });
        discoveryDialog.show();
        applyDialogColors(discoveryDialog);
        discoveryDialog.getListView().setAdapter(discoveryAdapter);
        discoveryDialog.getListView().setOnItemClickListener((parent, view, pos, id) -> {
            List<ProbeHelper.DiscoveredServer> list = DiscoveryService.getDiscoveredServers();
            if (pos >= 0 && pos < list.size()) {
                ProbeHelper.DiscoveredServer s = list.get(pos);
                String proto = s.probedProtocol;
                if (proto == null || proto.equals("OFFLINE") || proto.equals("Detecting…")) {
                    return;
                }
                String url = proto.toLowerCase() + "://" + s.host + ":" + s.port;
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            }
        });
        discoveryDialogShown = true;
        findServersButton.setText("Stop Discovery");
        startRefreshTimer();
    }

    private void refreshDiscoveryDialog() {
        if (discoveryDialog == null || !discoveryDialog.isShowing() || discoveryAdapter == null) return;
        List<String> labels = new ArrayList<>();
        List<ProbeHelper.DiscoveredServer> list = DiscoveryService.getDiscoveredServers();
        for (ProbeHelper.DiscoveredServer s : list) {
            String proto = s.probedProtocol;
            if (proto == null) proto = "Detecting…";
            labels.add(s.host + ":" + s.port + " " + proto);
        }
        discoveryAdapter.clear();
        discoveryAdapter.addAll(labels);
        discoveryAdapter.notifyDataSetChanged();
    }

    private void startRefreshTimer() {
        if (refreshTimerRunnable == null) {
            refreshTimerRunnable = new RefreshTimerTask();
        }
        uiHandler.removeCallbacks(refreshTimerRunnable);
        uiHandler.postDelayed(refreshTimerRunnable, REFRESH_INTERVAL_MS);
    }

    private void stopRefreshTimer() {
        if (refreshTimerRunnable != null) uiHandler.removeCallbacks(refreshTimerRunnable);
    }

    private void applyDialogColors(AlertDialog dialog) {
        int tc = getResources().getColor(R.color.text_primary);
        int sc = getResources().getColor(R.color.text_secondary);
        TextView tv = dialog.findViewById(android.R.id.title);
        if (tv != null) tv.setTextColor(tc);
        View root = dialog.getWindow().getDecorView();
        setAllTextViewColors(root, tc, sc);
        Button pos = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        Button neg = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (pos != null) pos.setTextColor(tc);
        if (neg != null) neg.setTextColor(tc);
    }

    private void setAllTextViewColors(View parent, int tc, int hc) {
        if (parent instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) parent).getChildCount(); i++) {
                View child = ((ViewGroup) parent).getChildAt(i);
                if (child instanceof TextView) {
                    ((TextView) child).setTextColor(tc);
                    if (((TextView) child).getHint() != null) ((TextView) child).setHintTextColor(hc);
                }
                if (child instanceof ViewGroup) setAllTextViewColors(child, tc, hc);
            }
        }
    }
}