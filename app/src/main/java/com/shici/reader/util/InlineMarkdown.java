package com.shici.reader.util;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;

import androidx.core.content.ContextCompat;

import com.shici.reader.R;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders the small amount of inline Markdown used by hand-written grammar
 * notes: **bold**, `code` and *italic*. Code gets a soft green background so
 * the important word or pattern stands out on the paper-coloured cards.
 */
public final class InlineMarkdown {

    private static final Pattern TOKEN = Pattern.compile(
            "(\\*\\*|__)(.+?)\\1|`([^`]+)`|(?<!\\*)\\*([^*\\n]+)\\*(?!\\*)|(?<!_)_([^_\\n]+)_(?!_)");

    private InlineMarkdown() {
    }

    public static CharSequence style(Context context, String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        SpannableStringBuilder result = new SpannableStringBuilder();
        Matcher matcher = TOKEN.matcher(raw);
        int cursor = 0;
        while (matcher.find()) {
            if (matcher.start() > cursor) {
                result.append(raw, cursor, matcher.start());
            }
            int start = result.length();
            String bold = matcher.group(2);
            String code = matcher.group(3);
            String italicStar = matcher.group(4);
            String italicUnderscore = matcher.group(5);
            if (bold != null) {
                result.append(bold);
                result.setSpan(new StyleSpan(Typeface.BOLD), start, result.length(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (code != null) {
                result.append(code);
                result.setSpan(new TypefaceSpan("monospace"), start, result.length(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                result.setSpan(new StyleSpan(Typeface.BOLD), start, result.length(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                result.setSpan(new ForegroundColorSpan(
                                ContextCompat.getColor(context, R.color.green_dark)),
                        start, result.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                result.setSpan(new BackgroundColorSpan(
                                ContextCompat.getColor(context, R.color.green_soft)),
                        start, result.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                String italic = italicStar != null ? italicStar : italicUnderscore;
                if (italic == null) {
                    italic = matcher.group();
                }
                result.append(italic);
                result.setSpan(new StyleSpan(Typeface.ITALIC), start, result.length(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            cursor = matcher.end();
        }
        if (cursor < raw.length()) {
            result.append(raw, cursor, raw.length());
        }
        return result;
    }
}
