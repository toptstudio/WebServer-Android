package www.webserver.com;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.widget.TextView;

public class SingleLineTextView2 extends TextView {
    public SingleLineTextView2(Context context, AttributeSet attributeSet, int i) {
        super(context, attributeSet, i);
        setSingleLine();
        setEllipsize(TextUtils.TruncateAt.END);
    }

    public SingleLineTextView2(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        setSingleLine();
        setEllipsize(TextUtils.TruncateAt.END);
    }

    public SingleLineTextView2(Context context) {
        super(context);
        setSingleLine();
        setEllipsize(TextUtils.TruncateAt.END);
    }
}
