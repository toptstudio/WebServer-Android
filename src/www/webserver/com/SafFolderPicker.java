package www.webserver.com;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

public class SafFolderPicker {
    public static final int REQUEST_CODE = 103;

    public interface Callback {
        void onSafFolderSelected(Uri treeUri);
    }

    public static void open(Activity activity) {
        if (Build.VERSION.SDK_INT >= 21) {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            activity.startActivityForResult(intent, REQUEST_CODE);
        }
    }

    public static void handleResult(int requestCode, int resultCode, Intent data, Callback callback) {
        if (requestCode == REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
            Uri treeUri = data.getData();
            if (treeUri != null) {
                callback.onSafFolderSelected(treeUri);
            }
        }
    }
}
