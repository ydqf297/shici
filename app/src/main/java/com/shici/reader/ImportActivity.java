package com.shici.reader;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.shici.reader.data.AppDatabase;
import com.shici.reader.databinding.ActivityImportBinding;
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

    private ActivityImportBinding binding;
    private String initialTitle = "";
    private String initialBody = "";
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

        binding.etBody.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateWordCount(s);
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
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateWordCount(binding.etBody.getText());
    }

    private void updateWordCount(CharSequence text) {
        int count = WordUtils.countWords(text == null ? "" : text.toString());
        binding.tvWordCount.setText(count == 0
                ? getString(R.string.word_count_zero)
                : getString(R.string.word_count, count));
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
        replaceBody(text.toString(), getString(R.string.pasted), true);
    }

    private void replaceBody(String text, String message, boolean restoreTitle) {
        String oldBody = binding.etBody.getText().toString();
        String oldTitle = binding.etTitle.getText().toString();
        binding.etBody.setText(text);
        binding.etBody.setSelection(binding.etBody.getText().length());
        Snackbar snackbar = Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
                .setAnchorView(binding.btnSave);
        snackbar.setAction(R.string.undo, view -> {
            binding.etBody.setText(oldBody);
            if (restoreTitle) {
                binding.etTitle.setText(oldTitle);
            }
            binding.etBody.setSelection(binding.etBody.getText().length());
        });
        snackbar.show();
    }

    private void fillSample() {
        String oldBody = binding.etBody.getText().toString();
        String oldTitle = binding.etTitle.getText().toString();
        binding.etBody.setText(SampleContent.BODY);
        binding.etBody.setSelection(0);
        if (binding.etTitle.getText().toString().trim().isEmpty()) {
            binding.etTitle.setText(SampleContent.TITLE);
        }
        Snackbar.make(binding.root, R.string.import_sample, Snackbar.LENGTH_LONG)
                .setAnchorView(binding.btnSave)
                .setAction(R.string.undo, view -> {
                    binding.etBody.setText(oldBody);
                    binding.etTitle.setText(oldTitle);
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
                    binding.etBody.setText(text);
                    binding.etBody.setSelection(0);
                    if (oldTitle.trim().isEmpty() && !suggestedTitle.isEmpty()) {
                        binding.etTitle.setText(suggestedTitle);
                    }
                    Snackbar.make(binding.root,
                                    getString(R.string.txt_loaded, fileName), Snackbar.LENGTH_LONG)
                            .setAnchorView(binding.btnSave)
                            .setAction(R.string.undo, view -> {
                                binding.etBody.setText(oldBody);
                                binding.etTitle.setText(oldTitle);
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
