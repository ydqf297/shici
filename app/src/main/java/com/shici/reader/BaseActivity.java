package com.shici.reader;

import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * Keeps every screen edge-to-edge without letting content run under the system
 * bars or the soft keyboard.
 */
public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setNavigationBarContrastEnforced(false);
            window.setStatusBarContrastEnforced(false);
        }
        applyBarIconAppearance();
    }

    protected void applyBarIconAppearance() {
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        boolean lightTheme = !isNightMode();
        controller.setAppearanceLightStatusBars(lightTheme);
        controller.setAppearanceLightNavigationBars(lightTheme);
    }

    protected boolean isNightMode() {
        int nightMode = getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    /**
     * Applies system bar + IME padding to a full-screen root view. The root
     * should not have its own top/bottom padding or the two will add up.
     */
    protected void applySafeInsets(View root) {
        final int initialLeft = root.getPaddingLeft();
        final int initialTop = root.getPaddingTop();
        final int initialRight = root.getPaddingRight();
        final int initialBottom = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets cutout = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout());
            Insets ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            int left = Math.max(bars.left, cutout.left);
            int top = Math.max(bars.top, cutout.top);
            int right = Math.max(bars.right, cutout.right);
            int bottom = Math.max(Math.max(bars.bottom, cutout.bottom), ime.bottom);
            view.setPadding(initialLeft + left, initialTop + top,
                    initialRight + right, initialBottom + bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    protected void openActivity(Intent intent) {
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.fade_out);
    }
}
