package com.shici.reader.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.graphics.Typeface;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.chip.Chip;
import com.shici.reader.App;
import com.shici.reader.R;
import com.shici.reader.TtsManager;
import com.shici.reader.data.AppDatabase;
import com.shici.reader.data.DictionaryRepository;
import com.shici.reader.databinding.SheetWordDetailBinding;
import com.shici.reader.model.WordEntry;
import com.shici.reader.model.WordForm;
import com.shici.reader.util.UiUtils;
import com.shici.reader.util.WordFormatting;
import com.shici.reader.util.WordUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class WordDetailSheet {

    private final Context context;
    private final BottomSheetDialog dialog;
    private final SheetWordDetailBinding binding;
    private final DictionaryRepository dictionary;
    private final String rawWord;
    private final String contextSentence;
    private final String sourceTitle;
    private final Runnable onWordbookChanged;

    private WordEntry currentEntry;
    private boolean saved;
    private boolean saveBusy;
    private int lookupGeneration;
    private int bookmarkGeneration;

    public WordDetailSheet(Context context, String rawWord,
                           @Nullable String contextSentence, @Nullable String sourceTitle) {
        this(context, rawWord, contextSentence, sourceTitle, null);
    }

    public WordDetailSheet(Context context, String rawWord,
                           @Nullable String contextSentence, @Nullable String sourceTitle,
                           @Nullable Runnable onWordbookChanged) {
        this.context = context;
        this.rawWord = rawWord == null ? "" : rawWord;
        this.contextSentence = contextSentence == null ? "" : contextSentence;
        this.sourceTitle = sourceTitle == null ? "" : sourceTitle;
        this.onWordbookChanged = onWordbookChanged;
        this.dictionary = DictionaryRepository.get(context);
        this.dialog = new BottomSheetDialog(context,
                R.style.ThemeOverlay_Shici_BottomSheet);
        this.binding = SheetWordDetailBinding.inflate(
                android.view.LayoutInflater.from(context));
        dialog.setContentView(binding.getRoot());

        binding.btnSave.setOnClickListener(view -> toggleSaved());
        binding.btnRetry.setOnClickListener(view -> load(true));
    }

    public void show() {
        if (dialog.isShowing()) {
            return;
        }
        dialog.show();
        applySheetSurface();
        expandSheet();
        load(false);
    }

    public void dismiss() {
        if (dialog.isShowing()) {
            dialog.dismiss();
        }
    }

    private void applySheetSurface() {
        View bottomSheet = dialog.findViewById(
                com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet == null) {
            return;
        }
        int surfaceColor = ContextCompat.getColor(context, R.color.surface);
        GradientDrawable background = new GradientDrawable();
        background.setColor(surfaceColor);
        float radius = UiUtils.dp(context, 28);
        background.setCornerRadii(new float[]{
                radius, radius,
                radius, radius,
                0f, 0f,
                0f, 0f
        });
        // The content root paints the visible sheet surface. Keeping the dialog
        // frame transparent means the rounded top corners are not boxed in by
        // the MaterialShapeDrawable supplied by the theme overlay.
        bottomSheet.setBackgroundColor(Color.TRANSPARENT);
        binding.getRoot().setBackground(background);
    }

    private void expandSheet() {
        View bottomSheet = dialog.findViewById(
                com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet != null) {
            BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
            behavior.setSkipCollapsed(true);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        }
    }

    private void load(boolean retry) {
        final int generation = ++lookupGeneration;
        showLoading();
        DictionaryRepository.Callback callback = new DictionaryRepository.Callback() {
            @Override
            public void onSuccess(WordEntry entry) {
                if (generation != lookupGeneration || !dialog.isShowing()) {
                    return;
                }
                currentEntry = entry;
                renderEntry(entry);
            }

            @Override
            public void onError() {
                if (generation != lookupGeneration || !dialog.isShowing()) {
                    return;
                }
                showError();
            }
        };
        if (retry) {
            dictionary.lookupAfterRetry(rawWord, callback);
        } else {
            dictionary.lookup(rawWord, callback);
        }
    }

    private void showLoading() {
        currentEntry = null;
        binding.loadingContainer.setVisibility(View.VISIBLE);
        binding.errorContainer.setVisibility(View.GONE);
        binding.detailScroll.setVisibility(View.GONE);
    }

    private void showError() {
        binding.loadingContainer.setVisibility(View.GONE);
        binding.detailScroll.setVisibility(View.GONE);
        binding.errorContainer.setVisibility(View.VISIBLE);
    }

    private void renderEntry(WordEntry entry) {
        binding.loadingContainer.setVisibility(View.GONE);
        binding.errorContainer.setVisibility(View.GONE);
        binding.detailScroll.setVisibility(View.VISIBLE);
        binding.detailScroll.scrollTo(0, 0);

        String displayWord = entry.found ? entry.word : rawWord.trim();
        if (displayWord.isEmpty()) {
            displayWord = entry.word;
        }
        binding.tvWord.setText(displayWord);

        String phonetic = WordUtils.prettyPhonetic(entry.phonetic);
        if (TextUtils.isEmpty(phonetic)) {
            binding.tvPhonetic.setVisibility(View.GONE);
        } else {
            binding.tvPhonetic.setVisibility(View.VISIBLE);
            binding.tvPhonetic.setText(
                    context.getString(R.string.phonetic_slash, phonetic));
        }

        String normalizedQuery = WordUtils.normalizeWord(rawWord);
        boolean showsBaseForm = entry.found && !normalizedQuery.isEmpty()
                && !normalizedQuery.equalsIgnoreCase(entry.word);
        if (showsBaseForm) {
            binding.tvBaseForm.setVisibility(View.VISIBLE);
            binding.tvBaseForm.setText(context.getString(R.string.base_form, normalizedQuery));
        } else {
            binding.tvBaseForm.setVisibility(View.GONE);
        }

        final String spokenWord = displayWord;
        binding.btnSpeak.setOnClickListener(view ->
                TtsManager.get(context).speak(spokenWord));

        if (!entry.found) {
            bookmarkGeneration++;
            binding.btnSave.setVisibility(View.GONE);
            binding.chipMeta.setVisibility(View.GONE);
            binding.meaningSection.setVisibility(View.GONE);
            binding.definitionSection.setVisibility(View.GONE);
            binding.formsSection.setVisibility(View.GONE);
            binding.notFoundContainer.setVisibility(View.VISIBLE);
            binding.tvNotFound.setText(context.getString(R.string.not_found, displayWord));
            renderContext(displayWord);
            expandSheet();
            binding.detailScroll.post(this::expandSheet);
            return;
        }

        binding.btnSave.setVisibility(View.VISIBLE);
        binding.notFoundContainer.setVisibility(View.GONE);
        renderChips(entry);
        renderTranslation(entry);
        renderDefinitions(entry);
        renderForms(entry);
        renderContext(entry.word);
        loadSavedState(entry.word);
        expandSheet();
        binding.detailScroll.post(this::expandSheet);
    }

    private void renderContext(String highlightWord) {
        if (TextUtils.isEmpty(contextSentence)) {
            binding.contextSection.setVisibility(View.GONE);
            return;
        }
        binding.contextSection.setVisibility(View.VISIBLE);
        binding.tvContext.setText(UiUtils.highlightContext(
                context, contextSentence, rawWord, highlightWord));
        if (TextUtils.isEmpty(sourceTitle)) {
            binding.tvContextSource.setVisibility(View.GONE);
        } else {
            binding.tvContextSource.setVisibility(View.VISIBLE);
            binding.tvContextSource.setText(
                    context.getString(R.string.saved_from, sourceTitle));
        }
    }

    private void renderTranslation(WordEntry entry) {
        if (TextUtils.isEmpty(entry.translation.trim())) {
            binding.meaningSection.setVisibility(View.GONE);
            return;
        }
        binding.meaningSection.setVisibility(View.VISIBLE);
        binding.tvTranslation.setText(
                WordFormatting.styleTranslation(context, entry.translation));
    }

    private void renderDefinitions(WordEntry entry) {
        if (TextUtils.isEmpty(entry.definition.trim())) {
            binding.definitionSection.setVisibility(View.GONE);
            return;
        }
        binding.definitionSection.setVisibility(View.VISIBLE);
        binding.tvDefinition.setText(
                WordFormatting.styleDefinitions(context, entry.definition));
    }

    private void renderForms(WordEntry entry) {
        binding.chipForms.removeAllViews();
        List<WordForm> forms = WordFormatting.parseForms(entry.exchange);
        int added = 0;
        for (WordForm form : forms) {
            if ("0".equals(form.key) && form.value.equalsIgnoreCase(entry.word)) {
                continue;
            }
            if (form.value.equalsIgnoreCase(entry.word)) {
                continue;
            }
            String label = WordFormatting.formLabel(context, form.key);
            SpannableStringBuilder text = new SpannableStringBuilder();
            text.append(label);
            int labelEnd = text.length();
            text.setSpan(new ForegroundColorSpan(
                            ContextCompat.getColor(context, R.color.ink_tertiary)),
                    0, labelEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.append("  ");
            int valueStart = text.length();
            text.append(form.value);
            text.setSpan(new ForegroundColorSpan(
                            ContextCompat.getColor(context, R.color.green_dark)),
                    valueStart, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setSpan(new StyleSpan(Typeface.BOLD), valueStart, text.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            Chip chip = UiUtils.addChip(binding.chipForms, text,
                    R.color.surface_alt, R.color.ink_secondary);
            chip.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            added++;
        }
        if (added == 0) {
            binding.formsSection.setVisibility(View.GONE);
        } else {
            binding.formsSection.setVisibility(View.VISIBLE);
        }
    }

    private void renderChips(WordEntry entry) {
        binding.chipMeta.removeAllViews();
        int count = 0;

        Set<String> tags = new LinkedHashSet<>();
        if (!TextUtils.isEmpty(entry.tag)) {
            for (String rawTag : entry.tag.trim().split("\\s+")) {
                if (!rawTag.isEmpty()) {
                    tags.add(rawTag);
                }
            }
        }
        for (String tag : tags) {
            String label = WordFormatting.tagLabel(context, tag);
            if (!TextUtils.isEmpty(label)) {
                UiUtils.addChip(binding.chipMeta, label,
                        R.color.green_soft, R.color.green_dark);
                count++;
            }
        }
        if (entry.oxford == 1) {
            UiUtils.addChip(binding.chipMeta, context.getString(R.string.tag_oxford),
                    R.color.amber_soft, R.color.amber_dark);
            count++;
        }
        String stars = WordFormatting.collinsLabel(entry.collins);
        if (!TextUtils.isEmpty(stars)) {
            UiUtils.addChip(binding.chipMeta,
                    context.getString(R.string.tag_collins, stars),
                    R.color.amber_soft, R.color.amber_dark);
            count++;
        }
        int rank = entry.frq > 0 ? entry.frq : entry.bnc;
        String frequency = WordFormatting.frequencyLabel(context, rank);
        if (!TextUtils.isEmpty(frequency)) {
            UiUtils.addChip(binding.chipMeta, frequency,
                    R.color.green_soft, R.color.green_dark);
            count++;
        }
        binding.chipMeta.setVisibility(count == 0 ? View.GONE : View.VISIBLE);
    }

    private void loadSavedState(String word) {
        saved = false;
        saveBusy = false;
        updateSaveButton();
        binding.btnSave.setEnabled(false);
        final int generation = ++bookmarkGeneration;
        App.io().execute(() -> {
            boolean isSaved;
            try {
                isSaved = AppDatabase.get(context).isWordSaved(word);
            } catch (RuntimeException exception) {
                isSaved = false;
            }
            final boolean result = isSaved;
            App.runOnMain(() -> {
                if (generation != bookmarkGeneration || !dialog.isShowing()) {
                    return;
                }
                saved = result;
                updateSaveButton();
                binding.btnSave.setEnabled(true);
            });
        });
    }

    private void updateSaveButton() {
        binding.btnSave.setIconResource(
                saved ? R.drawable.ic_bookmark : R.drawable.ic_bookmark_border);
        binding.btnSave.setContentDescription(context.getString(
                saved ? R.string.remove_from_wordbook : R.string.save_to_wordbook));
        binding.btnSave.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(context,
                        saved ? R.color.amber : R.color.amber_soft)));
        binding.btnSave.setIconTint(ColorStateList.valueOf(
                ContextCompat.getColor(context,
                        saved ? R.color.hero_on_accent : R.color.amber_dark)));
    }

    private void toggleSaved() {
        if (currentEntry == null || !currentEntry.found || saveBusy) {
            return;
        }
        saveBusy = true;
        binding.btnSave.setEnabled(false);
        bookmarkGeneration++;
        final WordEntry entry = currentEntry;
        final boolean removing = saved;
        App.io().execute(() -> {
            try {
                AppDatabase database = AppDatabase.get(context);
                if (removing) {
                    database.removeSavedWord(entry.word);
                } else {
                    database.saveWord(entry.word, entry.phonetic, entry.translation,
                            contextSentence, sourceTitle);
                }
                final boolean nowSaved = !removing;
                App.runOnMain(() -> {
                    saved = nowSaved;
                    saveBusy = false;
                    if (dialog.isShowing()) {
                        updateSaveButton();
                        binding.btnSave.setEnabled(true);
                    }
                    UiUtils.toast(context, nowSaved
                            ? R.string.added_word : R.string.removed_word);
                    if (onWordbookChanged != null) {
                        onWordbookChanged.run();
                    }
                });
            } catch (RuntimeException exception) {
                App.runOnMain(() -> {
                    saveBusy = false;
                    if (dialog.isShowing()) {
                        binding.btnSave.setEnabled(true);
                    }
                    UiUtils.toast(context, R.string.save_word_failed);
                });
            }
        });
    }
}
