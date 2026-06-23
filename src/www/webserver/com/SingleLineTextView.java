package www.webserver.com;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.widget.TextView;

public class SingleLineTextView extends TextView {
    public SingleLineTextView(Context context, AttributeSet attributeSet, int i) {
        super(context, attributeSet, i);
        setSingleLine();
        setEllipsize(TextUtils.TruncateAt.START);
    }

    public SingleLineTextView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        setSingleLine();
        setEllipsize(TextUtils.TruncateAt.START);
    }

    public SingleLineTextView(Context context) {
        super(context);
        setSingleLine();
        setEllipsize(TextUtils.TruncateAt.START);
    }
}
