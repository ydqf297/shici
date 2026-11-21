package com.shici.reader;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.BackgroundColorSpan;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.shici.reader.analysis.AnalysisDocument;
import com.shici.reader.analysis.AnalysisParseException;
import com.shici.reader.analysis.AnalysisParser;
import com.shici.reader.analysis.BreakdownItem;
import com.shici.reader.analysis.SentenceAnalysis;
import com.shici.reader.data.AppDatabase;
import com.shici.reader.databinding.ActivityAnalysisBinding;
import com.shici.reader.databinding.ItemAnalysisSentenceBinding;
import com.shici.reader.model.Article;
import com.shici.reader.ui.WordDetailSheet;
import com.shici.reader.util.InlineMarkdown;
import com.shici.reader.util.UiUtils;
import com.shici.reader.util.WordUtils;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;

public class AnalysisActivity extends BaseActivity {

    public static final String EXTRA_ARTICLE_ID = "article_id";

    private ActivityAnalysisBinding binding;
    private Article article;
    private AnalysisDocument document;
    private BackgroundColorSpan currentHighlight;
    private Spannable highlightTarget;
    private TextView highlightView;
    private int restoredScrollY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAnalysisBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySafeInsets(binding.getRoot());

        if (savedInstanceState != null) {
            restoredScrollY = savedInstanceState.getInt("scroll_y", 0);
        }

        binding.btnBack.setOnClickListener(view -> finish());
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
            AnalysisDocument parsed = null;
            try {
                loaded = AppDatabase.get(this).getArticle(articleId);
                if (loaded != null) {
                    parsed = AnalysisParser.parse(loaded.body);
                }
            } catch (RuntimeException | AnalysisParseException ignored) {
                // Handled by the null checks below.
            }
            Article finalArticle = loaded;
            AnalysisDocument finalDocument = parsed;
            App.runOnMain(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (finalArticle == null) {
                    UiUtils.toast(this, R.string.article_gone);
                    finish();
                    return;
                }
                if (finalDocument == null) {
                    UiUtils.toast(this, R.string.analysis_parse_failed);
                    finish();
                    return;
                }
                article = finalArticle;
                document = finalDocument;
                bindArticle();
            });
        });
    }

    private void bindArticle() {
        binding.tvTopTitle.setText(article.title);
        binding.tvArticleTitle.setText(article.title);
        binding.tvMeta.setText(getString(R.string.analysis_meta,
                document.sentenceCount(), document.wordCount(),
                WordUtils.formatDate(article.createdAt)));
        binding.tvSentenceCount.setText(
                getString(R.string.analysis_section_count, document.sentenceCount()));

        binding.tvSourcePassage.setText(buildClickableText(
                document.source, null, binding.tvSourcePassage));
        binding.tvSourcePassage.setMovementMethod(LinkMovementMethod.getInstance());
        binding.tvSourcePassage.setHighlightColor(Color.TRANSPARENT);
        binding.btnSpeakSource.setOnClickListener(view ->
                TtsManager.get(this).speak(document.source));

        renderSentences();
        binding.scroll.postDelayed(
                () -> binding.scroll.scrollTo(0, restoredScrollY), 60);
    }

    private void renderSentences() {
        binding.sentenceContainer.removeAllViews();
        List<SentenceAnalysis> sentences = document.sentences;
        for (int i = 0; i < sentences.size(); i++) {
            SentenceAnalysis sentence = sentences.get(i);
            ItemAnalysisSentenceBinding item = ItemAnalysisSentenceBinding.inflate(
                    getLayoutInflater(), binding.sentenceContainer, false);
            String index = String.format(Locale.CHINA, "%02d", i + 1);
            item.tvNumber.setText(index);
            item.tvSentenceLabel.setText(
                    getString(R.string.analysis_sentence_label, index));
            item.btnSpeak.setContentDescription(
                    getString(R.string.analysis_speak_sentence, i + 1));
            item.btnSpeak.setOnClickListener(view ->
                    TtsManager.get(this).speak(sentence.sentence));

            item.tvSentence.setText(buildClickableText(
                    sentence.sentence, sentence.sentence, item.tvSentence));
            item.tvSentence.setMovementMethod(LinkMovementMethod.getInstance());
            item.tvSentence.setHighlightColor(Color.TRANSPARENT);

            UiUtils.setVisible(item.breakdownSection, sentence.hasBreakdown());
            UiUtils.setVisible(item.translationSection, sentence.hasTranslation());
            UiUtils.setVisible(item.tipsSection, sentence.hasTips());

            if (sentence.hasBreakdown()) {
                renderBreakdown(item, sentence.breakdown);
            }
            if (sentence.hasTranslation()) {
                item.tvTranslation.setText(
                        InlineMarkdown.style(this, sentence.translation));
            }
            if (sentence.hasTips()) {
                renderTips(item, sentence.tips);
            }
            binding.sentenceContainer.addView(item.getRoot());
        }
    }

    private void renderBreakdown(ItemAnalysisSentenceBinding item,
                                 List<BreakdownItem> breakdown) {
        item.breakdownContainer.removeAllViews();
        for (BreakdownItem part : breakdown) {
            if (part.text.trim().isEmpty()) {
                continue;
            }
            SpannableStringBuilder line = new SpannableStringBuilder("·  ");
            line.setSpan(new ForegroundColorSpan(
                            ContextCompat.getColor(this, R.color.amber)),
                    0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            int textStart = line.length();
            line.append(part.text);
            line.setSpan(new StyleSpan(Typeface.BOLD),
                    textStart, line.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            line.setSpan(new ForegroundColorSpan(
                            ContextCompat.getColor(this, R.color.green_ink)),
                    textStart, line.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (!part.gloss.trim().isEmpty()) {
                line.append("   ");
                int glossStart = line.length();
                line.append(part.gloss);
                line.setSpan(new RelativeSizeSpan(0.88f),
                        glossStart, line.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                line.setSpan(new ForegroundColorSpan(
                                ContextCompat.getColor(this, R.color.ink_secondary)),
                        glossStart, line.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            TextView view = new TextView(this);
            view.setText(line);
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            view.setLineSpacing(UiUtils.dp(this, 3), 1f);
            view.setPadding(0, UiUtils.dp(this, 3), 0, UiUtils.dp(this, 3));
            item.breakdownContainer.addView(view);
        }
    }

    private void renderTips(ItemAnalysisSentenceBinding item, List<String> tips) {
        item.tipsContainer.removeAllViews();
        for (String tip : tips) {
            SpannableStringBuilder line = new SpannableStringBuilder("·  ");
            line.setSpan(new ForegroundColorSpan(
                            ContextCompat.getColor(this, R.color.amber)),
                    0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            line.append(InlineMarkdown.style(this, tip));
            TextView view = new TextView(this);
            view.setText(line);
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            view.setTextColor(ContextCompat.getColor(this, R.color.ink_secondary));
            view.setLineSpacing(UiUtils.dp(this, 3), 1f);
            view.setPadding(0, UiUtils.dp(this, 3), 0, UiUtils.dp(this, 3));
            item.tipsContainer.addView(view);
        }
    }

    private SpannableStringBuilder buildClickableText(
            String text, String fixedContext, TextView view) {
        String source = text == null ? "" : text;
        SpannableStringBuilder builder = new SpannableStringBuilder(source);
        int color = ContextCompat.getColor(this, R.color.ink);
        Matcher matcher = WordUtils.WORD_PATTERN.matcher(source);
        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            String context = fixedContext != null
                    ? fixedContext
                    : WordUtils.contextAround(source, start, end);
            builder.setSpan(new AnalysisWordSpan(
                            matcher.group(), context, start, end, builder, view, color),
                    start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return builder;
    }

    private void openWord(AnalysisWordSpan span) {
        span.view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        clearHighlight();
        currentHighlight = new BackgroundColorSpan(
                ContextCompat.getColor(this, R.color.highlight_word));
        highlightTarget = span.target;
        highlightView = span.view;
        span.target.setSpan(currentHighlight, span.start, span.end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        span.view.invalidate();

        new WordDetailSheet(this, span.word, span.context, article.title).show();
    }

    private void clearHighlight() {
        if (currentHighlight != null && highlightTarget != null) {
            highlightTarget.removeSpan(currentHighlight);
        }
        if (highlightView != null) {
            highlightView.invalidate();
        }
        currentHighlight = null;
        highlightTarget = null;
        highlightView = null;
    }

    private void showOverflowMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, getString(R.string.analysis_share));
        popup.getMenu().add(0, 2, 1, getString(R.string.article_menu_delete));
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    shareAnalysis();
                    return true;
                case 2:
                    confirmDelete();
                    return true;
                default:
                    return false;
            }
        });
        popup.show();
    }

    private void shareAnalysis() {
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

    private final class AnalysisWordSpan extends ClickableSpan {
        private final String word;
        private final String context;
        private final int start;
        private final int end;
        private final Spannable target;
        private final TextView view;
        private final int color;

        private AnalysisWordSpan(String word, String context, int start, int end,
                                 Spannable target, TextView view, int color) {
            this.word = word;
            this.context = context;
            this.start = start;
            this.end = end;
            this.target = target;
            this.view = view;
            this.color = color;
        }

        @Override
        public void onClick(@NonNull View widget) {
            openWord(this);
        }

        @Override
        public void updateDrawState(@NonNull TextPaint ds) {
            super.updateDrawState(ds);
            ds.setColor(color);
            ds.setUnderlineText(false);
        }
    }
}
