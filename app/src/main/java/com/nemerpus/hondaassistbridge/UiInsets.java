package com.nemerpus.hondaassistbridge;

import android.app.Activity;
import android.graphics.Insets;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;

final class UiInsets {
    static void protectTop(Activity activity, View root) {
        final int l=root.getPaddingLeft(), t=root.getPaddingTop(), r=root.getPaddingRight(), b=root.getPaddingBottom();
        if (Build.VERSION.SDK_INT >= 30) activity.getWindow().setDecorFitsSystemWindows(false);
        else activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        root.setOnApplyWindowInsetsListener((v, wi) -> {
            int top;
            if (Build.VERSION.SDK_INT >= 30) {
                Insets bars = wi.getInsets(WindowInsets.Type.statusBars() | WindowInsets.Type.displayCutout());
                top = bars.top;
            } else top = wi.getSystemWindowInsetTop();
            v.setPadding(l, t + top, r, b);
            return wi;
        });
        root.requestApplyInsets();
    }
    private UiInsets() {}
}
