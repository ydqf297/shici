package com.shici.reader.util;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;

import androidx.core.content.ContextCompat;

import com.shici.reader.R;
import com.shici.reader.model.WordForm;

import java.util.ArrayList;
import java.util.List;

/**
 * Presentation-only helpers for dictionary rows.
 */
public final class WordFormatting {

    private WordFormatting() {
    }

    public static CharSequence styleTranslation(Context context, String translation) {
        List<String[]> lines = WordUtils.splitTranslations(translation);
        SpannableStringBuilder builder = new SpannableStringBuilder();
        int green = ContextCompat.getColor(context, R.color.green);
        for (int i = 0; i < lines.size(); i++) {
            String[] parts = lines.get(i);
            if (!parts[0].isEmpty()) {
                int start = builder.length();
                builder.append(parts[0]);
                builder.append('.');
                builder.setSpan(new ForegroundColorSpan(green), start, builder.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.setSpan(new StyleSpan(Typeface.BOLD), start, builder.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.append("  ");
            }
            builder.append(parts[1]);
            if (i < lines.size() - 1) {
                builder.append('\n');
            }
        }
        return builder;
    }

    public static CharSequence styleDefinitions(Context context, String definition) {
        List<String[]> lines = WordUtils.splitDefinitions(definition);
        SpannableStringBuilder builder = new SpannableStringBuilder();
        int amber = ContextCompat.getColor(context, R.color.amber);
        for (int i = 0; i < lines.size(); i++) {
            String[] parts = lines.get(i);
            if (!parts[0].isEmpty()) {
                int start = builder.length();
                builder.append(parts[0]);
                builder.append('.');
                builder.setSpan(new ForegroundColorSpan(amber), start, builder.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.setSpan(new StyleSpan(Typeface.BOLD), start, builder.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.append("   ");
            }
            builder.append(parts[1]);
            if (i < lines.size() - 1) {
                builder.append('\n');
            }
        }
        return builder;
    }

    public static List<WordForm> parseForms(String exchange) {
        List<WordForm> forms = new ArrayList<>();
        if (exchange == null || exchange.trim().isEmpty()) {
            return forms;
        }
        String[] tokens = exchange.split("/");
        String[] orderedKeys = {"s", "3", "p", "d", "i", "r", "t", "0"};
        for (String key : orderedKeys) {
            for (String token : tokens) {
                int colon = token.indexOf(':');
                if (colon <= 0) {
                    continue;
                }
                String tokenKey = token.substring(0, colon).trim();
                String value = token.substring(colon + 1).trim();
                if (key.equals(tokenKey) && !value.isEmpty()) {
                    forms.add(new WordForm(key, value));
                    break;
                }
            }
        }
        return forms;
    }

    public static String formLabel(Context context, String key) {
        switch (key) {
            case "s":
                return context.getString(R.string.form_s);
            case "3":
                return context.getString(R.string.form_3);
            case "p":
                return context.getString(R.string.form_p);
            case "d":
                return context.getString(R.string.form_d);
            case "i":
                return context.getString(R.string.form_i);
            case "r":
                return context.getString(R.string.form_r);
            case "t":
                return context.getString(R.string.form_t);
            case "0":
                return context.getString(R.string.form_lemma);
            default:
                return context.getString(R.string.form_other);
        }
    }

    public static String tagLabel(Context context, String key) {
        switch (key) {
            case "zk":
                return context.getString(R.string.tag_zk);
            case "gk":
                return context.getString(R.string.tag_gk);
            case "cet4":
                return context.getString(R.string.tag_cet4);
            case "cet6":
                return context.getString(R.string.tag_cet6);
            case "ky":
                return context.getString(R.string.tag_ky);
            case "toefl":
                return context.getString(R.string.tag_toefl);
            case "ielts":
                return context.getString(R.string.tag_ielts);
            case "gre":
                return context.getString(R.string.tag_gre);
            default:
                return "";
        }
    }

    public static String frequencyLabel(Context context, int rank) {
        if (rank <= 0) {
            return "";
        }
        if (rank <= 5000) {
            return context.getString(R.string.frequency_high);
        }
        if (rank <= 15000) {
            return context.getString(R.string.frequency_mid);
        }
        return context.getString(R.string.frequency_low);
    }

    public static String collinsLabel(int stars) {
        if (stars <= 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < Math.min(5, stars); i++) {
            builder.append('\u2605');
        }
        return builder.toString();
    }
}
