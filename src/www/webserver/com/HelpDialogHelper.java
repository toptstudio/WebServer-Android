package www.webserver.com;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.widget.ScrollView;
import android.widget.TextView;
import android.view.View;
import android.view.ViewGroup;

public class HelpDialogHelper {

    public static void show(Activity activity) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("Server Unreachable");

        ScrollView scrollView = new ScrollView(activity);
        TextView textView = new TextView(activity);
        int padding = (int) (activity.getResources().getDisplayMetrics().density * 16.0f);
        textView.setPadding(padding, padding, padding, padding);
        textView.setTextSize(14.0f);
        textView.setTextColor(activity.getResources().getColor(R.color.text_primary));
        textView.setText(
            "If your server is not visible to other devices on the same Wi‑Fi, " +
            "the most common cause is a router setting that blocks local traffic.\n\n\n\n" +
            "1. Wireless Wi‑Fi Settings\n\n\n" +
            "AP/Client/Station Isolation\n\n✕  DISABLED\nAllows wireless devices to talk to each other\n\n\n" +
            "IGMP Snooping\n\n✕  DISABLED\nEnabled with multicast forwarding\n✓  ALLOWED\n" +
            "Prevents the router from dropping local discovery traffic\n\n\n" +
            "Multicast Forwarding\nBroadcast Traffic\n\n✓  ENABLED\n\n\n\n" +
            "2. Firewall & Security\n\n\n" +
            "LAN‑to‑LAN Firewall\n\n✕  DISABLED\nSome routers block traffic between LAN clients\n\n\n" +
            "MAC Filtering\nAccess Control\n\n✕  DISABLED\nAdd both devices to allow list\n\n\n" +
            "UPnP\n\n✓  ENABLED\n\n\n\n" +
            "3. Local Network (LAN/DHCP)\n\n\n" +
            "Subnet Mask\n\n(e.g., 255.255.255.0)\nMust be identical on all devices\n\n\n" +
            "IP Pool\n\n(e.g., all 192.168.1.x)\nNo mixing between different ranges,\n" +
            "All devices must get IPs from the same DHCP range\n\n\n\n" +
            "4. Android‑Specific Checks\n\n\n" +
            "Location permission\n\n✓  GRANTED\nRequired for Wi‑Fi scanning Android 8+\n\n\n" +
            "Battery optimisation\n\n✓  EXCLUDED for this app\n\n\n" +
            "Both devices on the same Wi‑Fi band\n\n2.4 GHz vs 5 GHz\nSome routers isolate bands.\n\n\n\n" +
            "After changing settings,\nRestart both devices\n\n" +
            "Tap 'Stop Discovery' then\nTap 'Find Servers' again."
        );

        scrollView.addView(textView);
        builder.setView(scrollView);
        builder.setPositiveButton("OK", (DialogInterface.OnClickListener) null);

        AlertDialog dialog = builder.create();
        dialog.show();
        applyDialogColors(activity, dialog);
    }

    private static void applyDialogColors(Activity activity, AlertDialog dialog) {
        int primaryColor = activity.getResources().getColor(R.color.text_primary);
        int secondaryColor = activity.getResources().getColor(R.color.text_secondary);
        try {
            TextView titleView = dialog.findViewById(android.R.id.title);
            if (titleView != null) titleView.setTextColor(primaryColor);
        } catch (Exception ignored) {}
        View root = dialog.getWindow().getDecorView();
        setAllTextViewColors(root, primaryColor, secondaryColor);
        try {
            ((TextView) dialog.getButton(AlertDialog.BUTTON_POSITIVE)).setTextColor(primaryColor);
            ((TextView) dialog.getButton(AlertDialog.BUTTON_NEUTRAL)).setTextColor(primaryColor);
            ((TextView) dialog.getButton(AlertDialog.BUTTON_NEGATIVE)).setTextColor(primaryColor);
        } catch (Exception ignored) {}
    }

    private static void setAllTextViewColors(View parent, int primaryColor, int hintColor) {
        if (parent instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) parent;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child instanceof TextView) {
                    TextView tv = (TextView) child;
                    tv.setTextColor(primaryColor);
                    if (tv.getHint() != null) tv.setHintTextColor(hintColor);
                }
                if (child instanceof ViewGroup) {
                    setAllTextViewColors(child, primaryColor, hintColor);
                }
            }
        }
    }
}
