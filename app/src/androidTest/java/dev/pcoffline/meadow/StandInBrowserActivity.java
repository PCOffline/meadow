package dev.pcoffline.meadow;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A stand-in browser for the end-to-end tests: its address bar shows the address it was opened
 * with, under the same id as Chrome's. Debug builds of Meadow read it like a real browser's
 * (BuildConfig.TEST_BROWSER); release builds never do.
 *
 * <p>In Java on purpose: opened on its own, it runs in the test APK's own process, which has none
 * of Kotlin's runtime (that's in Meadow's APK), so a Kotlin activity would crash on its first null
 * check.
 */
public class StandInBrowserActivity extends Activity {
    public static final String EXTRA_ADDRESS = "address";

    private TextView bar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bar = new TextView(this);
        bar.setId(dev.pcoffline.meadow.test.R.id.url_bar);
        TextView page = new TextView(this);
        page.setText("A page");
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(bar);
        layout.addView(page);
        setContentView(layout);
        show(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        show(intent);
    }

    private void show(Intent intent) {
        String address = intent.getStringExtra(EXTRA_ADDRESS);
        bar.setText(address == null ? "" : address);
    }
}
