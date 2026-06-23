package www.webserver.com;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public final class UrlUtils {

    private UrlUtils() {}

    public static String encodePathSegment(String name) {
        if (name == null || name.isEmpty()) return name;
        try {
            return URLEncoder.encode(name, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            return name;
        }
    }

    public static String sanitizeFolderName(String input) {
        if (input == null) return "";
        return input.replace("|", "")
                    .replace("\\", "")
                    .replace(">", "")
                    .replace("<", "")
                    .replace("/", "")
                    .replace("*", "")
                    .replace("\"", "")
                    .replace(":", "")
                    .replace("?", "");
    }
}
