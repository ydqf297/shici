package com.shici.reader;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.shici.reader.data.AppDatabase;
import com.shici.reader.databinding.ActivityWordbookBinding;
import com.shici.reader.model.SavedWord;
import com.shici.reader.ui.WordDetailSheet;
import com.shici.reader.ui.WordListAdapter;
import com.shici.reader.util.UiUtils;

import java.util.ArrayList;
import java.util.List;

public class WordbookActivity extends BaseActivity implements WordListAdapter.Listener {

    private ActivityWordbookBinding binding;
    private WordListAdapter adapter;
    private final List<SavedWord> words = new ArrayList<>();
    private int loadGeneration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityWordbookBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySafeInsets(binding.getRoot());

        binding.btnBack.setOnClickListener(view -> finish());
        binding.btnEmptyAction.setOnClickListener(view -> finish());
        binding.rvWords.setLayoutManager(new LinearLayoutManager(this));
        adapter = new WordListAdapter(this);
        binding.rvWords.setAdapter(adapter);

        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s == null ? "" : s.toString().trim();
                adapter.setQuery(query);
                UiUtils.setVisible(binding.btnClearSearch, !query.isEmpty());
                updateEmptyState();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        binding.btnClearSearch.setOnClickListener(view -> binding.etSearch.setText(""));
        binding.etSearch.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                UiUtils.hideKeyboard(binding.etSearch);
                return true;
            }
            return false;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadWords();
    }

    private void loadWords() {
        final int generation = ++loadGeneration;
        App.io().execute(() -> {
            List<SavedWord> result;
            try {
                result = AppDatabase.get(this).getSavedWords();
            } catch (RuntimeException exception) {
                result = null;
            }
            final List<SavedWord> loaded = result;
            App.runOnMain(() -> {
                if (generation != loadGeneration || isFinishing() || isDestroyed()) {
                    return;
                }
                if (loaded == null) {
                    UiUtils.toast(this, R.string.generic_error);
                    return;
                }
                words.clear();
                words.addAll(loaded);
                adapter.submitList(loaded);
                binding.tvCount.setText(getString(R.string.wordbook_count, loaded.size()));
                UiUtils.setVisible(binding.searchBar, !loaded.isEmpty());
                updateEmptyState();
            });
        });
    }

    private void updateEmptyState() {
        int total = adapter == null ? 0 : adapter.getTotalCount();
        int visible = adapter == null ? 0 : adapter.getVisibleCount();
        boolean empty = visible == 0;
        UiUtils.setVisible(binding.emptyState, empty);
        UiUtils.setVisible(binding.rvWords, !empty);
        if (!empty) {
            return;
        }
        if (total == 0) {
            binding.ivEmptyIcon.setImageResource(R.drawable.ic_bookmark_border);
            binding.tvEmptyTitle.setText(R.string.wordbook_empty_title);
            binding.tvEmptySubtitle.setText(R.string.wordbook_empty_subtitle);
            UiUtils.setVisible(binding.btnEmptyAction, true);
        } else {
            binding.ivEmptyIcon.setImageResource(R.drawable.ic_search);
            binding.tvEmptyTitle.setText(R.string.no_results);
            binding.tvEmptySubtitle.setText(R.string.search_no_results_subtitle);
            UiUtils.setVisible(binding.btnEmptyAction, false);
        }
    }

    @Override
    public void onWordClick(SavedWord word) {
        WordDetailSheet sheet = new WordDetailSheet(
                this, word.word, word.context, word.sourceTitle, this::loadWords);
        sheet.show();
    }

    @Override
    public void onRemoveClick(SavedWord word) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.remove_word_title)
                .setMessage(getString(R.string.remove_word_message, word.word))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.remove, (dialog, which) -> removeWord(word))
                .show();
    }

    private void removeWord(SavedWord word) {
        App.io().execute(() -> {
            try {
                AppDatabase.get(this).removeSavedWord(word.word);
                App.runOnMain(() -> {
                    UiUtils.toast(this, R.string.removed_word);
                    loadWords();
                });
            } catch (RuntimeException exception) {
                App.runOnMain(() -> UiUtils.toast(this, R.string.save_word_failed));
            }
        });
    }
}
