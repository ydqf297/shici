package com.shici.reader.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.shici.reader.R;
import com.shici.reader.databinding.ItemSavedWordBinding;
import com.shici.reader.model.SavedWord;
import com.shici.reader.util.UiUtils;
import com.shici.reader.util.WordUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class WordListAdapter extends RecyclerView.Adapter<WordListAdapter.WordViewHolder> {

    public interface Listener {
        void onWordClick(SavedWord word);

        void onRemoveClick(SavedWord word);
    }

    private final List<SavedWord> allWords = new ArrayList<>();
    private final List<SavedWord> visibleWords = new ArrayList<>();
    private final Listener listener;
    private String query = "";

    public WordListAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submitList(List<SavedWord> words) {
        allWords.clear();
        if (words != null) {
            allWords.addAll(words);
        }
        applyFilter();
    }

    public void setQuery(String newQuery) {
        query = newQuery == null ? "" : newQuery.trim().toLowerCase(Locale.ROOT);
        applyFilter();
    }

    public int getTotalCount() {
        return allWords.size();
    }

    public int getVisibleCount() {
        return visibleWords.size();
    }

    @SuppressLint("NotifyDataSetChanged")
    private void applyFilter() {
        visibleWords.clear();
        if (query.isEmpty()) {
            visibleWords.addAll(allWords);
        } else {
            for (SavedWord word : allWords) {
                String haystack = (word.word + " " + word.translation + " "
                        + word.sourceTitle).toLowerCase(Locale.ROOT);
                if (haystack.contains(query)) {
                    visibleWords.add(word);
                }
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public WordViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemSavedWordBinding binding = ItemSavedWordBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new WordViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull WordViewHolder holder, int position) {
        holder.bind(visibleWords.get(position));
    }

    @Override
    public int getItemCount() {
        return visibleWords.size();
    }

    public final class WordViewHolder extends RecyclerView.ViewHolder {
        private final ItemSavedWordBinding binding;

        private WordViewHolder(ItemSavedWordBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        private void bind(SavedWord word) {
            Context context = binding.getRoot().getContext();
            binding.tvWord.setText(word.word);
            String phonetic = WordUtils.prettyPhonetic(word.phonetic);
            if (TextUtils.isEmpty(phonetic)) {
                binding.tvPhonetic.setVisibility(android.view.View.GONE);
            } else {
                binding.tvPhonetic.setVisibility(android.view.View.VISIBLE);
                binding.tvPhonetic.setText(
                        context.getString(R.string.phonetic_slash, phonetic));
            }
            binding.tvTranslation.setText(TextUtils.isEmpty(word.translation)
                    ? context.getString(R.string.no_meanings)
                    : UiUtils.compactGloss(word.translation));

            String source = TextUtils.isEmpty(word.sourceTitle)
                    ? context.getString(R.string.saved_manually)
                    : context.getString(R.string.saved_from, word.sourceTitle);
            binding.tvSource.setText(context.getString(R.string.source_date, source,
                    WordUtils.formatDate(word.createdAt)));

            binding.getRoot().setOnClickListener(view -> listener.onWordClick(word));
            binding.btnRemove.setOnClickListener(view -> listener.onRemoveClick(word));
            binding.btnRemove.setContentDescription(
                    context.getString(R.string.remove_from_wordbook));
        }
    }
}
