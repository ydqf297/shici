package com.shici.reader;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.PopupMenu;

import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.shici.reader.data.AppDatabase;
import com.shici.reader.databinding.ActivityMainBinding;
import com.shici.reader.databinding.ItemArticleBinding;
import com.shici.reader.model.Article;
import com.shici.reader.util.UiUtils;
import com.shici.reader.util.WordUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends BaseActivity {

    private ActivityMainBinding binding;
    private final List<Article> articles = new ArrayList<>();
    private int loadGeneration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySafeInsets(binding.getRoot());
        binding.hero.setClipToOutline(true);

        binding.btnImport.setOnClickListener(view -> openImport());
        binding.btnEmptyImport.setOnClickListener(view -> openImport());
        binding.btnWordbook.setOnClickListener(view -> openActivity(
                new Intent(this, WordbookActivity.class)));
        binding.btnEmptySample.setOnClickListener(view -> addSampleAndRead());
        binding.dictionaryCredit.setOnClickListener(view -> showDictionaryInfo());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        final int generation = ++loadGeneration;
        App.io().execute(() -> {
            try {
                AppDatabase database = AppDatabase.get(this);
                List<Article> result = database.getArticles();
                int savedCount = database.getSavedWordCount();
                App.runOnMain(() -> {
                    if (generation != loadGeneration || isFinishing() || isDestroyed()) {
                        return;
                    }
                    articles.clear();
                    articles.addAll(result);
                    render(result, savedCount);
                });
            } catch (RuntimeException exception) {
                App.runOnMain(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        UiUtils.toast(this, R.string.generic_error);
                    }
                });
            }
        });
    }

    private void render(List<Article> result, int savedCount) {
        binding.tvHeroArticles.setText(String.valueOf(result.size()));
        binding.tvHeroWords.setText(String.valueOf(savedCount));
        binding.tvLibraryCount.setText(getString(R.string.library_count, result.size()));

        binding.articleContainer.removeAllViews();
        boolean empty = result.isEmpty();
        UiUtils.setVisible(binding.emptyState, empty);
        UiUtils.setVisible(binding.articleContainer, !empty);

        for (int i = 0; i < result.size(); i++) {
            Article article = result.get(i);
            ItemArticleBinding item = ItemArticleBinding.inflate(
                    getLayoutInflater(), binding.articleContainer, false);
            item.tvIndex.setText(String.format(Locale.CHINA, "%02d", i + 1));
            item.tvTitle.setText(article.title);

            String previewText = article.body.replace('\n', ' ').replaceAll("\\s+", " ").trim();
            String title = article.title.trim();
            if (!title.isEmpty() && previewText.regionMatches(true, 0, title, 0, title.length())) {
                int afterTitle = title.length();
                boolean titleEndsAtWordBoundary = afterTitle == previewText.length()
                        || !Character.isLetterOrDigit(previewText.charAt(afterTitle));
                if (titleEndsAtWordBoundary) {
                    previewText = previewText.substring(afterTitle).trim();
                    previewText = previewText.replaceFirst(
                            "^[\\p{Punct}\\u2013\\u2014\\s]+", "");
                }
            }
            if (previewText.isEmpty()) {
                item.tvPreview.setVisibility(View.GONE);
            } else {
                item.tvPreview.setText(previewText);
            }

            item.tvMeta.setText(buildMeta(article));
            item.getRoot().setOnClickListener(view -> openReader(article.id));
            View.OnClickListener menuClick = view -> showArticleMenu(view, article);
            item.btnMore.setOnClickListener(menuClick);
            item.getRoot().setOnLongClickListener(view -> {
                showArticleMenu(item.btnMore, article);
                return true;
            });

            binding.articleContainer.addView(item.getRoot());
        }
    }

    private CharSequence buildMeta(Article article) {
        SpannableStringBuilder meta = new SpannableStringBuilder();
        if (article.sample) {
            int start = meta.length();
            meta.append(getString(R.string.sample_badge));
            meta.setSpan(new ForegroundColorSpan(
                            ContextCompat.getColor(this, R.color.amber)),
                    start, meta.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            meta.setSpan(new StyleSpan(Typeface.BOLD),
                    start, meta.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            meta.append("   ·   ");
        }
        meta.append(getString(R.string.word_count, WordUtils.countWords(article.body)));
        meta.append("   ·   ");
        meta.append(WordUtils.formatDate(article.createdAt));
        return meta;
    }

    private void showArticleMenu(View anchor, Article article) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, getString(R.string.article_menu_delete));
        popup.setOnMenuItemClickListener(menuItem -> {
            if (menuItem.getItemId() == 1) {
                confirmDelete(article);
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void confirmDelete(Article article) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_title)
                .setMessage(getString(R.string.delete_message, article.title))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) -> deleteArticle(article.id))
                .show();
    }

    private void deleteArticle(long id) {
        App.io().execute(() -> {
            try {
                AppDatabase.get(this).deleteArticle(id);
                App.runOnMain(this::refresh);
            } catch (RuntimeException exception) {
                App.runOnMain(() -> UiUtils.toast(this, R.string.delete_failed));
            }
        });
    }

    private void openImport() {
        openActivity(new Intent(this, ImportActivity.class));
    }

    private void openReader(long articleId) {
        Intent intent = new Intent(this, ReaderActivity.class);
        intent.putExtra(ReaderActivity.EXTRA_ARTICLE_ID, articleId);
        openActivity(intent);
    }

    private void addSampleAndRead() {
        App.io().execute(() -> {
            try {
                long id = AppDatabase.get(this).insertArticle(
                        SampleContent.TITLE, SampleContent.BODY, true);
                App.runOnMain(() -> openReader(id));
            } catch (RuntimeException exception) {
                App.runOnMain(() -> UiUtils.toast(this, R.string.save_failed));
            }
        });
    }

    private void showDictionaryInfo() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.about_dictionary_title)
                .setMessage(R.string.about_dictionary_text)
                .setPositiveButton(R.string.close, null)
                .show();
    }
}
