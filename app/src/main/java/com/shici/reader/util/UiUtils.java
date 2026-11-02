package com.shici.reader.util;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorRes;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.shici.reader.R;

import java.util.Locale;

public final class UiUtils {

    private UiUtils() {
    }

    public static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static void toast(Context context, @StringRes int message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    public static void toast(Context context, String message) {
        if (!TextUtils.isEmpty(message)) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        }
    }

    public static int color(Context context, @ColorRes int color) {
        return ContextCompat.getColor(context, color);
    }

    public static void setVisible(View view, boolean visible) {
        view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    public static void hideKeyboard(View view) {
        InputMethodManager manager = (InputMethodManager) view.getContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) {
            manager.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    public static void showKeyboard(View view) {
        view.requestFocus();
        InputMethodManager manager = (InputMethodManager) view.getContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) {
            manager.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    public static Chip addChip(ChipGroup group, CharSequence text,
                               @ColorRes int background, @ColorRes int foreground) {
        Chip chip = (Chip) LayoutInflater.from(group.getContext())
                .inflate(R.layout.view_chip_tag, group, false);
        chip.setText(text);
        chip.setChipBackgroundColor(ColorStateList.valueOf(color(group.getContext(), background)));
        chip.setTextColor(color(group.getContext(), foreground));
        chip.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        group.addView(chip);
        return chip;
    }

    /**
     * Highlights the first occurrence of any candidate in a sentence. Used in
     * the context card so the tapped word is easy to spot.
     */
    public static CharSequence highlightContext(Context context, String sentence,
                                                String... candidates) {
        if (TextUtils.isEmpty(sentence)) {
            return "";
        }
        SpannableStringBuilder result = new SpannableStringBuilder(sentence);
        String lower = sentence.toLowerCase(Locale.ROOT);
        int start = -1;
        int end = -1;
        for (String candidate : candidates) {
            if (TextUtils.isEmpty(candidate)) {
                continue;
            }
            String needle = candidate.toLowerCase(Locale.ROOT);
            int index = lower.indexOf(needle);
            if (index >= 0 && (start < 0 || index < start)) {
                start = index;
                end = index + needle.length();
            }
        }
        if (start >= 0) {
            result.setSpan(new BackgroundColorSpan(
                            color(context, R.color.highlight_word_context)),
                    start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            result.setSpan(new ForegroundColorSpan(color(context, R.color.ink)),
                    start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            result.setSpan(new StyleSpan(Typeface.BOLD), start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return result;
    }

    /** A compact one-line gloss used on wordbook cards. */
    public static CharSequence compactGloss(String translation) {
        if (TextUtils.isEmpty(translation)) {
            return "";
        }
        String compact = translation.replace("\n", "  ·  ").trim();
        return compact;
    }

    public static String ellipsize(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String oneLine = text.replace('\n', ' ').trim();
        if (oneLine.length() <= maxLength) {
            return oneLine;
        }
        return oneLine.substring(0, Math.max(0, maxLength - 1)).trim() + "…";
    }

    public static void setTextOrGone(TextView view, CharSequence text) {
        if (TextUtils.isEmpty(text)) {
            view.setVisibility(View.GONE);
        } else {
            view.setText(text);
            view.setVisibility(View.VISIBLE);
        }
    }
}
