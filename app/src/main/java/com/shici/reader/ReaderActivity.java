package com.shici.reader;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.BackgroundColorSpan;
import android.text.style.ClickableSpan;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.shici.reader.data.AppDatabase;
import com.shici.reader.databinding.ActivityReaderBinding;
import com.shici.reader.databinding.DialogFontSizeBinding;
import com.shici.reader.model.Article;
import com.shici.reader.ui.WordDetailSheet;
import com.shici.reader.util.UiUtils;
import com.shici.reader.util.WordUtils;

import java.util.regex.Matcher;

public class ReaderActivity extends BaseActivity {

    public static final String EXTRA_ARTICLE_ID = "article_id";
    private static final String PREFS = "reader_prefs";
    private static final String KEY_FONT_INDEX = "font_index";
    private static final float[] FONT_SIZES = {15f, 18f, 21f, 24f};

    private ActivityReaderBinding binding;
    private Article article;
    private String displayText = "";
    private SpannableStringBuilder passage;
    private BackgroundColorSpan currentHighlight;
    private int fontIndex = 1;
    private int restoredScrollY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityReaderBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySafeInsets(binding.getRoot());

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        fontIndex = Math.max(0, Math.min(FONT_SIZES.length - 1,
                prefs.getInt(KEY_FONT_INDEX, 1)));
        if (savedInstanceState != null) {
            restoredScrollY = savedInstanceState.getInt("scroll_y", 0);
        }

        binding.btnBack.setOnClickListener(view -> finish());
        binding.btnFontSize.setOnClickListener(view -> showFontSizeDialog());
        binding.btnMore.setOnClickListener(this::showOverflowMenu);
        binding.btnOpenWordbook.setOnClickListener(view -> openWordbook());

        long articleId = getIntent().getLongExtra(EXTRA_ARTICLE_ID, -1L);
        if (articleId < 0) {
            finish();
            return;
        }
        loadArticle(articleId);
    }

    private void loadArticle(long articleId) {
        App.io().execute(() -> {
            Article loaded = null;
            try {
                loaded = AppDatabase.get(this).getArticle(articleId);
            } catch (RuntimeException ignored) {
                // Handled by the null check below.
            }
            Article finalArticle = loaded;
            App.runOnMain(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (finalArticle == null) {
                    UiUtils.toast(this, R.string.article_gone);
                    finish();
                    return;
                }
                article = finalArticle;
                bindArticle();
            });
        });
    }

    private void bindArticle() {
        binding.tvTopTitle.setText(article.title);
        binding.tvArticleTitle.setText(article.title);
        int words = WordUtils.countWords(article.body);
        binding.tvEyebrow.setText(getString(R.string.reading_meta, words));
        StringBuilder meta = new StringBuilder();
        if (article.sample) {
            meta.append(getString(R.string.sample_badge)).append("   ·   ");
        }
        meta.append(WordUtils.formatDate(article.createdAt));
        meta.append("   ·   ");
        meta.append(getString(R.string.read_time, WordUtils.readingMinutes(words)));
        binding.tvMeta.setText(meta.toString());

        displayText = stripDuplicateTitle(article.body, article.title);
        buildPassage();
        binding.scroll.postDelayed(() -> binding.scroll.scrollTo(0, restoredScrollY), 60);
    }

    private String stripDuplicateTitle(String body, String title) {
        String cleaned = WordUtils.cleanBody(body);
        if (title == null || title.trim().isEmpty()) {
            return cleaned;
        }
        int newline = cleaned.indexOf('\n');
        if (newline > 0) {
            String firstLine = cleaned.substring(0, newline).trim();
            if (firstLine.equalsIgnoreCase(title.trim())) {
                return cleaned.substring(newline + 1).trim();
            }
        }
        return cleaned;
    }

    private void buildPassage() {
        passage = new SpannableStringBuilder(displayText);
        int textColor = ContextCompat.getColor(this, R.color.ink);
        Matcher matcher = WordUtils.WORD_PATTERN.matcher(displayText);
        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            passage.setSpan(new WordSpan(matcher.group(), start, end, textColor),
                    start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        binding.tvPassage.setText(passage);
        binding.tvPassage.setMovementMethod(LinkMovementMethod.getInstance());
        binding.tvPassage.setHighlightColor(Color.TRANSPARENT);
        binding.tvPassage.setTextSize(TypedValue.COMPLEX_UNIT_SP, FONT_SIZES[fontIndex]);
        binding.tvPassage.setLineSpacing(UiUtils.dp(this, 9), 1.0f);
    }

    private void onWordTapped(String word, int start, int end) {
        if (passage == null) {
            return;
        }
        binding.tvPassage.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        if (currentHighlight != null) {
            passage.removeSpan(currentHighlight);
        }
        currentHighlight = new BackgroundColorSpan(
                ContextCompat.getColor(this, R.color.highlight_word));
        passage.setSpan(currentHighlight, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        binding.tvPassage.invalidate();

        String context = WordUtils.contextAround(displayText, start, end);
        WordDetailSheet sheet = new WordDetailSheet(this, word, context, article.title);
        sheet.show();
    }

    private void showOverflowMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, getString(R.string.reader_font_size));
        popup.getMenu().add(0, 2, 1, getString(R.string.share_article));
        popup.getMenu().add(0, 3, 2, getString(R.string.article_menu_delete));
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    showFontSizeDialog();
                    return true;
                case 2:
                    shareArticle();
                    return true;
                case 3:
                    confirmDelete();
                    return true;
                default:
                    return false;
            }
        });
        popup.show();
    }

    private void showFontSizeDialog() {
        DialogFontSizeBinding dialogBinding = DialogFontSizeBinding.inflate(getLayoutInflater());
        final int initialIndex = fontIndex;
        final boolean[] confirmed = {false};
        dialogBinding.seekFontSize.setProgress(fontIndex);
        applyPreviewSize(dialogBinding, FONT_SIZES[fontIndex]);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.font_size_title)
                .setView(dialogBinding.getRoot())
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.done, null)
                .create();
        dialog.setOnDismissListener(d -> {
            if (!confirmed[0]) {
                applyFontSize(initialIndex);
            }
        });
        dialogBinding.seekFontSize.setOnSeekBarChangeListener(
                new android.widget.SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(android.widget.SeekBar seekBar, int progress,
                                                  boolean fromUser) {
                        int index = Math.max(0, Math.min(FONT_SIZES.length - 1, progress));
                        applyPreviewSize(dialogBinding, FONT_SIZES[index]);
                        applyFontSize(index);
                    }

                    @Override
                    public void onStartTrackingTouch(android.widget.SeekBar seekBar) {
                    }

                    @Override
                    public void onStopTrackingTouch(android.widget.SeekBar seekBar) {
                    }
                });
        dialog.show();
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    int index = Math.max(0, Math.min(FONT_SIZES.length - 1,
                            dialogBinding.seekFontSize.getProgress()));
                    confirmed[0] = true;
                    fontIndex = index;
                    applyFontSize(index);
                    getSharedPreferences(PREFS, MODE_PRIVATE)
                            .edit().putInt(KEY_FONT_INDEX, index).apply();
                    dialog.dismiss();
                });
    }

    private void applyPreviewSize(DialogFontSizeBinding dialogBinding, float sizeSp) {
        dialogBinding.tvFontPreview.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
    }

    private void applyFontSize(int index) {
        fontIndex = index;
        if (binding != null) {
            binding.tvPassage.setTextSize(TypedValue.COMPLEX_UNIT_SP, FONT_SIZES[index]);
        }
    }

    private void shareArticle() {
        if (article == null) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, article.title);
        intent.putExtra(Intent.EXTRA_TEXT, article.title + "\n\n" + article.body);
        startActivity(Intent.createChooser(intent, getString(R.string.share_chooser)));
    }

    private void confirmDelete() {
        if (article == null) {
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_title)
                .setMessage(getString(R.string.delete_message, article.title))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) -> deleteArticle())
                .show();
    }

    private void deleteArticle() {
        if (article == null) {
            return;
        }
        final long id = article.id;
        App.io().execute(() -> {
            try {
                AppDatabase.get(this).deleteArticle(id);
                App.runOnMain(this::finish);
            } catch (RuntimeException exception) {
                App.runOnMain(() -> UiUtils.toast(this, R.string.delete_failed));
            }
        });
    }

    private void openWordbook() {
        openActivity(new Intent(this, WordbookActivity.class));
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (binding != null) {
            outState.putInt("scroll_y", binding.scroll.getScrollY());
        }
    }

    private final class WordSpan extends ClickableSpan {
        private final String word;
        private final int start;
        private final int end;
        private final int color;

        private WordSpan(String word, int start, int end, int color) {
            this.word = word;
            this.start = start;
            this.end = end;
            this.color = color;
        }

        @Override
        public void onClick(@NonNull View widget) {
            onWordTapped(word, start, end);
        }

        @Override
        public void updateDrawState(@NonNull TextPaint ds) {
            super.updateDrawState(ds);
            ds.setColor(color);
            ds.setUnderlineText(false);
        }
    }
}
