package com.nemerpus.hondaassistbridge;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

/**
 * Small presentation-only toolkit. It deliberately contains no navigation,
 * persistence, BLE or business logic so the existing app architecture stays intact.
 */
final class UiKit {
    private UiKit() {}

    static final int BG       = Color.rgb(8, 9, 11);
    static final int SURFACE  = Color.rgb(18, 21, 25);
    static final int SURFACE2 = Color.rgb(25, 29, 34);
    static final int STROKE   = Color.rgb(43, 48, 55);
    static final int TEXT     = Color.rgb(248, 249, 251);
    static final int MUTED    = Color.rgb(151, 158, 168);
    static final int RED      = Color.rgb(226, 31, 43);
    static final int RED_SOFT = Color.rgb(67, 24, 29);
    static final int GOOD     = Color.rgb(83, 217, 139);
    static final int WARN     = Color.rgb(245, 185, 75);
    static final int INFO     = Color.rgb(102, 174, 255);

    static void applySystemBars(Activity a) {
        a.getWindow().setStatusBarColor(BG);
        a.getWindow().setNavigationBarColor(BG);
    }

    static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static TextView text(Context c, String value, int sp, int color) {
        TextView v = new TextView(c);
        v.setText(value);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setFontFeatureSettings("kern");
        return v;
    }

    static TextView title(Context c, String value) {
        TextView v = text(c, value, 29, TEXT);
        v.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        v.setLetterSpacing(-0.015f);
        return v;
    }

    static TextView overline(Context c, String value) {
        TextView v = text(c, value, 11, MUTED);
        v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        v.setLetterSpacing(.15f);
        return v;
    }

    static TextView section(Context c, String value) {
        TextView v = overline(c, value);
        v.setPadding(0, dp(c, 4), 0, dp(c, 10));
        return v;
    }

    static TextView body(Context c, String value) {
        TextView v = text(c, value, 14, MUTED);
        v.setLineSpacing(0, 1.12f);
        return v;
    }

    static LinearLayout card(Context c) {
        LinearLayout v = new LinearLayout(c);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(dp(c, 18), dp(c, 17), dp(c, 18), dp(c, 17));
        v.setBackground(roundRect(c, SURFACE, 24, STROKE, 1));
        return v;
    }

    static void makeClickableCard(Context c, View v) {
        GradientDrawable base = roundRect(c, SURFACE, 24, STROKE, 1);
        GradientDrawable mask = roundRect(c, Color.WHITE, 24, Color.TRANSPARENT, 0);
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(0x24FFFFFF), base, mask));
        v.setClickable(true);
        v.setFocusable(true);
    }

    static Button primaryButton(Context c, String label) {
        return button(c, label, RED, Color.WHITE, RED, 0);
    }

    static Button secondaryButton(Context c, String label) {
        return button(c, label, SURFACE2, TEXT, STROKE, 1);
    }

    static Button dangerButton(Context c, String label) {
        return button(c, label, Color.TRANSPARENT, RED, RED, 1);
    }

    private static Button button(Context c, String label, int fill, int text, int stroke, int strokeDp) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTextColor(text);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(c, 54));
        b.setMinimumHeight(dp(c, 54));
        b.setPadding(dp(c, 18), 0, dp(c, 18), 0);
        b.setStateListAnimator(null);
        GradientDrawable base = roundRect(c, fill, 18, stroke, strokeDp);
        GradientDrawable mask = roundRect(c, Color.WHITE, 18, Color.TRANSPARENT, 0);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x26FFFFFF), base, mask));
        return b;
    }

    static void field(Context c, EditText e) {
        e.setTextColor(TEXT);
        e.setHintTextColor(MUTED);
        e.setTextSize(15);
        e.setSingleLine(true);
        e.setPadding(dp(c, 16), 0, dp(c, 16), 0);
        e.setMinHeight(dp(c, 54));
        e.setBackground(roundRect(c, SURFACE2, 16, STROKE, 1));
    }

    static void spinner(Context c, Spinner s) {
        s.setPadding(dp(c, 12), 0, dp(c, 12), 0);
        s.setMinimumHeight(dp(c, 54));
        s.setBackground(roundRect(c, SURFACE2, 16, STROKE, 1));
    }

    static TextView chip(Context c, String label, int textColor, int fill) {
        TextView v = text(c, label, 12, textColor);
        v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(c, 11), dp(c, 7), dp(c, 11), dp(c, 7));
        v.setBackground(roundRect(c, fill, 999, Color.TRANSPARENT, 0));
        return v;
    }

    static TextView iconTile(Context c, String glyph, int foreground) {
        TextView v = text(c, glyph, 21, foreground);
        v.setGravity(Gravity.CENTER);
        v.setBackground(roundRect(c, SURFACE2, 16, STROKE, 1));
        return v;
    }

    static View spacer(Context c, int h) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(c, h)));
        return v;
    }

    static GradientDrawable roundRect(Context c, int fill, int radiusDp, int stroke, int strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(c, radiusDp));
        if (strokeDp > 0) g.setStroke(dp(c, strokeDp), stroke);
        return g;
    }
}
