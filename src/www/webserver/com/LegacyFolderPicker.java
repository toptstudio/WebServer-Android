package www.webserver.com;

import android.app.Activity;
import android.app.Dialog;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.BaseAdapter;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LegacyFolderPicker {
    public interface OnFolderSelectedListener {
        void onFolderSelected(String absolutePath);
    }

    private final Activity activity;
    private final OnFolderSelectedListener folderListener;
    private Dialog dialog;
    private String currentPath = "/";

    public LegacyFolderPicker(Activity activity, OnFolderSelectedListener folderListener) {
        this.activity = activity;
        this.folderListener = folderListener;
    }

    public void show() {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        showDialog();
    }

    public void dismiss() {
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
    }

    private void showDialog() {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        if (dialog != null && dialog.isShowing()) dialog.dismiss();
        dialog = new Dialog(activity);
        try { dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE); } catch (Exception ignored) {}
        dialog.setContentView(R.layout.selector);

        TextView tvPath = (TextView) dialog.findViewById(R.id.pathTitle);
        ListView lv = (ListView) dialog.findViewById(R.id.listview);
        Button btnOK = (Button) dialog.findViewById(R.id.dialogButtonOK);
        Button btnStor = (Button) dialog.findViewById(R.id.dialogButtonStorage);
        btnOK.setTextColor(0xFF000000);
        btnStor.setTextColor(0xFF000000);

        updateFileList(tvPath, lv);
        lv.setOnItemClickListener((parent, view, pos, id) -> {
            String name = ((TextView) view.findViewById(R.id.grid_item_label)).getText().toString();
            if (!name.equals("..")) {
                currentPath += name + "/";
                updateFileList(tvPath, lv);
            } else {
                File pf = new File(currentPath).getParentFile();
                if (pf != null) {
                    currentPath = pf.getAbsolutePath();
                    if (!currentPath.endsWith("/")) currentPath += "/";
                }
                updateFileList(tvPath, lv);
            }
        });
        btnOK.setOnClickListener(v -> {
            String path = currentPath;
            if (path.endsWith("/") && path.length() > 1) path = path.substring(0, path.length() - 1);
            folderListener.onFolderSelected(path);
            dialog.dismiss();
        });
        btnStor.setOnClickListener(v -> {
            currentPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/";
            updateFileList(tvPath, lv);
        });
        dialog.show();
    }

    private void updateFileList(TextView title, ListView listView) {
        title.setText(currentPath);
        List<String> dirs = new ArrayList<>();
        String lookupPath = currentPath;
        if (lookupPath.endsWith("/") && lookupPath.length() > 1) {
            lookupPath = lookupPath.substring(0, lookupPath.length() - 1);
        }

        if (lookupPath.equals("") || lookupPath.equals("/") || lookupPath.equals("/storage")) {
            File storageDir = new File("/storage");
            File[] volumes = storageDir.listFiles();
            if (volumes != null) {
                for (File f : volumes) {
                    if (f.isDirectory() && f.getName().matches("[A-F0-9]{4}-[A-F0-9]{4}")) {
                        dirs.add(f.getName());
                    }
                }
            }
        }

        List<String> staticList = StaticTreeProvider.getChildren(lookupPath);
        if (staticList != null) {
            for (String s : staticList) {
                if (!dirs.contains(s)) dirs.add(s);
            }
        } else {
            File dir = new File(currentPath);
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isDirectory() && !f.getName().startsWith(".")) dirs.add(f.getName());
                }
            }
            if (dirs.isEmpty() && RootChecker.isDeviceRooted()) {
                final WeakReference<Activity> activityRef = new WeakReference<>(activity);
                final WeakReference<ListView> listViewRef = new WeakReference<>(listView);
                final WeakReference<TextView> titleRef = new WeakReference<>(title);
                new Thread(() -> {
                    try {
                        Process p = Runtime.getRuntime().exec("su -c ls -1p " + escapeShellArg(currentPath));
                        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
                        List<String> rootDirs = new ArrayList<>();
                        String line;
                        while ((line = r.readLine()) != null) {
                            if (line.endsWith("/")) {
                                String nm = line.substring(0, line.length() - 1);
                                if (!nm.startsWith(".")) rootDirs.add(nm);
                            }
                        }
                        p.waitFor();
                        Activity act = activityRef.get();
                        if (act != null && !act.isFinishing() && !act.isDestroyed()) {
                            act.runOnUiThread(() -> {
                                dirs.addAll(rootDirs);
                                if (!currentPath.equals("/")) dirs.add("..");
                                Collections.sort(dirs, String.CASE_INSENSITIVE_ORDER);
                                ListView lv = listViewRef.get();
                                if (lv != null) {
                                    lv.setAdapter(new PickerAdapter(act, dirs));
                                }
                            });
                        }
                    } catch (Exception ignored) {}
                }).start();
                return;
            }
        }

        if (!currentPath.equals("/")) dirs.add("..");
        Collections.sort(dirs, String.CASE_INSENSITIVE_ORDER);
        listView.setAdapter(new PickerAdapter(activity, dirs));
    }

    private String escapeShellArg(String arg) {
        return "'" + arg.replace("'", "'\\''") + "'";
    }

    private static class PickerAdapter extends BaseAdapter {
        private final Activity activity;
        private final List<String> items;

        PickerAdapter(Activity activity, List<String> items) {
            this.activity = activity;
            this.items = new ArrayList<>(items);
        }

        @Override public int getCount() { return items.size(); }
        @Override public String getItem(int i) { return items.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int pos, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(activity).inflate(R.layout.row, parent, false);
            }
            ImageView imageView = (ImageView) convertView.findViewById(R.id.grid_item_image);
            TextView textView = (TextView) convertView.findViewById(R.id.grid_item_label);
            imageView.setImageResource(R.drawable.folder_icon);
            textView.setText(items.get(pos));
            return convertView;
        }
    }
}