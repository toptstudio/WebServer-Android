package www.webserver.com;

import android.os.Environment;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AppLogger {
    private static final String FOLDER_NAME = "Web Server";
    private static final String FILE_NAME = "log.txt";
    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);

    private static File getLogFile() {
        File dir = new File(Environment.getExternalStorageDirectory(), FOLDER_NAME);
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, FILE_NAME);
    }

    public static synchronized void log(String tag, String message) {
        log(tag, message, null);
    }

    public static synchronized void log(String tag, String message, Throwable e) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(getLogFile(), true))) {
            pw.print(sdf.format(new Date()));
            pw.print(" [");
            pw.print(tag);
            pw.print("] ");
            pw.println(message);
            if (e != null) {
                e.printStackTrace(pw);
            }
            pw.flush();
        } catch (IOException ignored) {}
    }
}