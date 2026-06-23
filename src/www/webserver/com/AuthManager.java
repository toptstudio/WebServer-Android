package www.webserver.com;

import android.util.Base64;
import org.json.JSONObject;

public class AuthManager {
    private static volatile String[] credentials = new String[]{"", ""};

    public static void loadFromJson(JSONObject json) {
        String user = json.optString("username", "");
        String pass = json.optString("password", "");
        credentials = new String[]{user, pass};   // optString never returns null, so no null check needed
    }

    public static void saveToJson(JSONObject json) {
        try {
            json.put("username", credentials[0]);
            json.put("password", credentials[1]);
        } catch (Exception ignored) {}
    }

    public static boolean isAuthRequired() {
        return !credentials[0].isEmpty() && !credentials[1].isEmpty();
    }

    public static void saveCredentials(String user, String pass) {
        credentials = new String[]{user != null ? user : "", pass != null ? pass : ""};
    }

    public static String getUsername() { return credentials[0]; }
    public static String getPassword() { return credentials[1]; }

    public static boolean checkAuthorization(String authHeader) {
        // Snapshot the credentials array once to avoid a TOCTOU race condition (H-1)
        final String[] creds = credentials;
        if (creds[0].isEmpty() || creds[1].isEmpty()) return true;

        if (authHeader == null || !authHeader.toLowerCase().startsWith("basic ")) return false;
        String base64 = authHeader.substring(6).trim();
        String decoded;
        try {
            // Use Base64.NO_WRAP instead of Base64.DEFAULT to prevent MIME line breaks (L1)
            decoded = new String(Base64.decode(base64, Base64.NO_WRAP), "UTF-8");
        } catch (Exception e) { return false; }
        int colon = decoded.indexOf(':');
        String reqUser = colon >= 0 ? decoded.substring(0, colon) : decoded;
        String reqPass = colon >= 0 ? decoded.substring(colon + 1) : "";
        return creds[0].equals(reqUser) && creds[1].equals(reqPass);
    }
}