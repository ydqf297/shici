package com.shici.reader;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.shici.reader.analysis.AnalysisDocument;
import com.shici.reader.analysis.AnalysisParseException;
import com.shici.reader.analysis.AnalysisParser;
import com.shici.reader.data.AppDatabase;
import com.shici.reader.databinding.ActivityImportBinding;
import com.shici.reader.model.Article;
import com.shici.reader.util.UiUtils;
import com.shici.reader.util.WordUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

public class ImportActivity extends BaseActivity {

    private static final int MAX_FILE_BYTES = 1024 * 1024;
    private static final int MAX_TITLE_LENGTH = 80;
    private static final long STATUS_DELAY_MS = 280L;

    private ActivityImportBinding binding;
    private final Handler statusHandler = new Handler(Looper.getMainLooper());
    private final Runnable statusRunnable = this::refreshStatusNow;
    private String initialTitle = "";
    private String initialBody = "";
    private int importKind = Article.KIND_READING;
    private boolean saved;
    private boolean saving;

    private final ActivityResultLauncher<String> filePicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::onFilePicked);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityImportBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySafeInsets(binding.getRoot());

        binding.etTitle.setFilters(new InputFilter[]{
                new InputFilter.LengthFilter(MAX_TITLE_LENGTH)});

        binding.btnBack.setOnClickListener(view -> attemptClose());
        binding.btnPaste.setOnClickListener(view -> pasteFromClipboard());
        binding.btnTxt.setOnClickListener(view -> filePicker.launch("text/*"));
        binding.btnExample.setOnClickListener(view -> fillSample());
        binding.btnSave.setOnClickListener(view -> saveArticle());
        binding.btnHelp.setOnClickListener(view -> showFormatHelp());
        binding.btnModeReading.setOnClickListener(
                view -> selectMode(Article.KIND_READING, true));
        binding.btnModeAnalysis.setOnClickListener(
                view -> selectMode(Article.KIND_ANALYSIS, true));

        binding.etBody.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (importKind == Article.KIND_READING) {
                    refreshStatusNow();
                } else {
                    scheduleStatusRefresh();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                attemptClose();
            }
        });

        selectMode(Article.KIND_READING, false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatusNow();
    }

    @Override
    protected void onDestroy() {
        statusHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void selectMode(int kind, boolean refresh) {
        importKind = kind;
        applyModeUi();
        if (refresh) {
            refreshStatusNow();
        }
    }

    private void applyModeUi() {
        boolean analysis = importKind == Article.KIND_ANALYSIS;
        UiUtils.setVisible(binding.btnHelp, analysis);
        binding.tvModeHint.setText(analysis
                ? R.string.import_mode_analysis_hint : R.string.import_mode_reading_hint);
        binding.tvPassageLabel.setText(analysis
                ? R.string.analysis_import_label : R.string.passage_label);
        binding.etBody.setHint(analysis
                ? R.string.analysis_import_hint : R.string.passage_hint);
        styleModeButton(binding.btnModeReading, !analysis);
        styleModeButton(binding.btnModeAnalysis, analysis);
    }

    private void styleModeButton(TextView button, boolean selected) {
        button.setSelected(selected);
        button.setBackgroundResource(selected
                ? R.drawable.bg_segment_selected : R.drawable.bg_segment_unselected);
        button.setTextColor(ContextCompat.getColor(this,
                selected ? R.color.paper : R.color.ink_secondary));
    }

    private void scheduleStatusRefresh() {
        statusHandler.removeCallbacks(statusRunnable);
        statusHandler.postDelayed(statusRunnable, STATUS_DELAY_MS);
    }

    private void refreshStatusNow() {
        statusHandler.removeCallbacks(statusRunnable);
        if (binding == null) {
            return;
        }
        if (importKind == Article.KIND_READING) {
            int count = WordUtils.countWords(binding.etBody.getText().toString());
            binding.tvWordCount.setText(count == 0
                    ? getString(R.string.word_count_zero)
                    : getString(R.string.word_count, count));
            binding.tvWordCount.setTextColor(ContextCompat.getColor(this, R.color.green));
            return;
        }

        String text = binding.etBody.getText().toString();
        if (text.trim().isEmpty()) {
            binding.tvWordCount.setText(R.string.word_count_zero);
            binding.tvWordCount.setTextColor(
                    ContextCompat.getColor(this, R.color.ink_tertiary));
            return;
        }
        if (text.length() > 80_000) {
            binding.tvWordCount.setText(R.string.analysis_too_long);
            binding.tvWordCount.setTextColor(ContextCompat.getColor(this, R.color.error));
            return;
        }
        try {
            AnalysisDocument document = AnalysisParser.parse(text);
            binding.tvWordCount.setText(getString(R.string.analysis_recognized_count,
                    document.sentenceCount(), document.wordCount()));
            binding.tvWordCount.setTextColor(ContextCompat.getColor(this, R.color.green));
        } catch (AnalysisParseException exception) {
            binding.tvWordCount.setText(R.string.analysis_not_recognized);
            binding.tvWordCount.setTextColor(ContextCompat.getColor(this, R.color.error));
        }
    }

    private void pasteFromClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip()) {
            UiUtils.toast(this, R.string.clipboard_empty);
            return;
        }
        ClipData clip = clipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) {
            UiUtils.toast(this, R.string.clipboard_empty);
            return;
        }
        CharSequence text = clip.getItemAt(0).coerceToText(this);
        if (text == null || text.toString().trim().isEmpty()) {
            UiUtils.toast(this, R.string.clipboard_empty);
            return;
        }
        replaceBody(text.toString(), getString(R.string.pasted));
    }

    private void replaceBody(String text, String message) {
        String oldBody = binding.etBody.getText().toString();
        int oldKind = importKind;
        binding.etBody.setText(text);
        binding.etBody.setSelection(binding.etBody.getText().length());
        boolean detected = detectAndSwitchToAnalysis(text);
        String snackMessage = detected
                ? message + " · " + getString(R.string.analysis_detected) : message;
        Snackbar snackbar = Snackbar.make(binding.root, snackMessage, Snackbar.LENGTH_LONG)
                .setAnchorView(binding.btnSave);
        snackbar.setAction(R.string.undo, view -> {
            binding.etBody.setText(oldBody);
            selectMode(oldKind, false);
            binding.etBody.setSelection(binding.etBody.getText().length());
        });
        snackbar.show();
    }

    private boolean detectAndSwitchToAnalysis(String text) {
        if (importKind != Article.KIND_READING || !AnalysisParser.looksLikeAnalysis(text)) {
            return false;
        }
        selectMode(Article.KIND_ANALYSIS, true);
        return true;
    }

    private void fillSample() {
        String oldBody = binding.etBody.getText().toString();
        String oldTitle = binding.etTitle.getText().toString();
        int oldKind = importKind;
        boolean analysis = importKind == Article.KIND_ANALYSIS;
        String sampleBody = analysis ? SampleContent.ANALYSIS : SampleContent.BODY;
        String sampleTitle = analysis ? SampleContent.ANALYSIS_TITLE : SampleContent.TITLE;

        binding.etBody.setText(sampleBody);
        binding.etBody.setSelection(0);
        String currentTitle = binding.etTitle.getText().toString().trim();
        if (currentTitle.isEmpty()
                || currentTitle.equals(SampleContent.TITLE)
                || currentTitle.equals(SampleContent.ANALYSIS_TITLE)) {
            binding.etTitle.setText(sampleTitle);
        }
        Snackbar.make(binding.root, R.string.sample_loaded, Snackbar.LENGTH_LONG)
                .setAnchorView(binding.btnSave)
                .setAction(R.string.undo, view -> {
                    binding.etBody.setText(oldBody);
                    binding.etTitle.setText(oldTitle);
                    selectMode(oldKind, false);
                })
                .show();
    }

    private void showFormatHelp() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.analysis_format_title)
                .setMessage(R.string.analysis_format_message)
                .setNegativeButton(R.string.close, null)
                .setPositiveButton(R.string.analysis_format_fill, (dialog, which) -> {
                    selectMode(Article.KIND_ANALYSIS, false);
                    fillSample();
                })
                .show();
    }

    private void saveArticle() {
        if (saving) {
            return;
        }
        String body = WordUtils.cleanBody(binding.etBody.getText().toString());
        if (body.length() > 80_000) {
            UiUtils.toast(this, R.string.text_too_long);
            binding.etBody.requestFocus();
            return;
        }

        if (importKind == Article.KIND_READING && AnalysisParser.looksLikeAnalysis(body)) {
            try {
                AnalysisParser.parse(body);
                selectMode(Article.KIND_ANALYSIS, false);
            } catch (AnalysisParseException ignored) {
                // Keep the explicit reading mode when the structure is incomplete.
            }
        }

        if (importKind == Article.KIND_ANALYSIS) {
            saveAnalysis(body);
            return;
        }
        saveReading(body);
    }

    private void saveReading(String body) {
        int words = WordUtils.countWords(body);
        if (body.isEmpty() || words == 0) {
            UiUtils.toast(this, body.isEmpty() ? R.string.empty_passage : R.string.empty_words);
            binding.etBody.requestFocus();
            return;
        }
        String title = binding.etTitle.getText().toString().trim();
        if (title.isEmpty()) {
            title = WordUtils.autoTitle(body);
        }
        if (title.isEmpty()) {
            title = getString(R.string.untitled_article);
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            title = title.substring(0, MAX_TITLE_LENGTH);
        }

        saving = true;
        binding.btnSave.setEnabled(false);
        UiUtils.hideKeyboard(binding.etBody);
        final String finalTitle = title;
        final String finalBody = body;
        App.io().execute(() -> {
            try {
                AppDatabase.get(this).insertArticle(finalTitle, finalBody, false);
                App.runOnMain(() -> {
                    saved = true;
                    UiUtils.toast(this, R.string.saved_to_library);
                    finish();
                });
            } catch (RuntimeException exception) {
                App.runOnMain(() -> {
                    saving = false;
                    binding.btnSave.setEnabled(true);
                    UiUtils.toast(this, R.string.save_failed);
                });
            }
        });
    }

    private void saveAnalysis(String body) {
        AnalysisDocument document;
        try {
            document = AnalysisParser.parse(body);
        } catch (AnalysisParseException exception) {
            UiUtils.toast(this, R.string.analysis_parse_error);
            binding.etBody.requestFocus();
            return;
        }
        if (document.wordCount() == 0) {
            UiUtils.toast(this, R.string.empty_words);
            binding.etBody.requestFocus();
            return;
        }
        String title = binding.etTitle.getText().toString().trim();
        if (title.isEmpty()) {
            title = document.suggestedTitle();
        }
        if (title.isEmpty()) {
            title = getString(R.string.untitled_article);
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            title = title.substring(0, MAX_TITLE_LENGTH);
        }

        saving = true;
        binding.btnSave.setEnabled(false);
        UiUtils.hideKeyboard(binding.etBody);
        final String finalTitle = title;
        final String finalBody = body;
        final String source = document.source;
        final int wordCount = document.wordCount();
        App.io().execute(() -> {
            try {
                AppDatabase.get(this).insertArticle(finalTitle, finalBody, false,
                        Article.KIND_ANALYSIS, source, wordCount);
                App.runOnMain(() -> {
                    saved = true;
                    UiUtils.toast(this, R.string.saved_to_library);
                    finish();
                });
            } catch (RuntimeException exception) {
                App.runOnMain(() -> {
                    saving = false;
                    binding.btnSave.setEnabled(true);
                    UiUtils.toast(this, R.string.save_failed);
                });
            }
        });
    }

    private void onFilePicked(Uri uri) {
        if (uri == null) {
            return;
        }
        App.io().execute(() -> {
            try {
                byte[] data = readFile(uri);
                if (data.length > MAX_FILE_BYTES) {
                    App.runOnMain(() -> UiUtils.toast(this, R.string.txt_too_large));
                    return;
                }
                String text = decodeText(data);
                String fileName = queryDisplayName(uri);
                String suggestedTitle = WordUtils.titleFromFileName(fileName);
                App.runOnMain(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    String oldBody = binding.etBody.getText().toString();
                    String oldTitle = binding.etTitle.getText().toString();
                    int oldKind = importKind;
                    binding.etBody.setText(text);
                    binding.etBody.setSelection(0);
                    if (oldTitle.trim().isEmpty() && !suggestedTitle.isEmpty()) {
                        binding.etTitle.setText(suggestedTitle);
                    }
                    boolean detected = detectAndSwitchToAnalysis(text);
                    String message = getString(R.string.txt_loaded, fileName);
                    if (detected) {
                        message += " · " + getString(R.string.analysis_detected);
                    }
                    Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
                            .setAnchorView(binding.btnSave)
                            .setAction(R.string.undo, view -> {
                                binding.etBody.setText(oldBody);
                                binding.etTitle.setText(oldTitle);
                                selectMode(oldKind, false);
                            })
                            .show();
                });
            } catch (IOException | RuntimeException exception) {
                App.runOnMain(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        UiUtils.toast(this, R.string.txt_read_failed);
                    }
                });
            }
        });
    }

    private byte[] readFile(Uri uri) throws IOException {
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) {
                throw new IOException("No stream for uri");
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[16 * 1024];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_FILE_BYTES) {
                    break;
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private String decodeText(byte[] data) {
        if (data.length >= 3 && (data[0] & 0xFF) == 0xEF
                && (data[1] & 0xFF) == 0xBB && (data[2] & 0xFF) == 0xBF) {
            return new String(data, 3, data.length - 3, StandardCharsets.UTF_8);
        }
        if (data.length >= 2 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xFE) {
            return new String(data, 2, data.length - 2, StandardCharsets.UTF_16LE);
        }
        if (data.length >= 2 && (data[0] & 0xFF) == 0xFE && (data[1] & 0xFF) == 0xFF) {
            return new String(data, 2, data.length - 2, StandardCharsets.UTF_16BE);
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(data))
                    .toString();
        } catch (CharacterCodingException exception) {
            return new String(data, Charset.forName("GB18030"));
        }
    }

    private String queryDisplayName(Uri uri) {
        String fallback = getString(R.string.import_txt);
        try (Cursor cursor = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String name = cursor.getString(index);
                    if (name != null && !name.trim().isEmpty()) {
                        return name;
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // Content providers are not required to expose a display name.
        }
        String path = uri.getLastPathSegment();
        return path == null ? fallback : path;
    }

    private void attemptClose() {
        if (saved || saving) {
            return;
        }
        String title = binding.etTitle.getText().toString();
        String body = binding.etBody.getText().toString();
        if (title.equals(initialTitle) && body.equals(initialBody)) {
            finish();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.discard_title)
                .setMessage(R.string.discard_message)
                .setNegativeButton(R.string.keep_editing, null)
                .setPositiveButton(R.string.discard, (dialog, which) -> finish())
                .show();
    }
}
