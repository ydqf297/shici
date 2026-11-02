package com.shici.reader.data;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import com.shici.reader.model.WordEntry;
import com.shici.reader.util.WordUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Read-only, fully offline dictionary stored as a SQLite asset. The database is
 * expanded once into app-private storage, then every lookup is local.
 */
@SuppressLint("StaticFieldLeak") // Holds an application context only.
public final class DictionaryRepository {

    public interface Callback {
        void onSuccess(WordEntry entry);

        void onError();
    }

    private static final String ASSET_NAME = "dictionary.db";
    private static final String DB_FILE_NAME = "dictionary_v1.db";
    private static final String PREFS = "dictionary_meta";
    private static final String KEY_ASSET_VERSION = "asset_version";
    private static final int ASSET_VERSION = 1;
    private static final int MIN_VALID_SIZE = 1_000_000;

    private static volatile DictionaryRepository instance;

    private final Context context;
    private final Handler mainHandler;
    private final ExecutorService executor;
    private final LruCache<String, WordEntry> cache = new LruCache<>(256);
    private SQLiteDatabase database;
    private IOException prepareError;

    public static DictionaryRepository get(Context context) {
        if (instance == null) {
            synchronized (DictionaryRepository.class) {
                if (instance == null) {
                    instance = new DictionaryRepository(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private DictionaryRepository(Context context) {
        this.context = context;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            private int count;

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "shici-dictionary-" + (++count));
                thread.setPriority(Thread.NORM_PRIORITY - 1);
                return thread;
            }
        });
    }

    public void warmUp() {
        executor.execute(() -> {
            try {
                ensureDatabase();
            } catch (IOException ignored) {
                // The lookup UI surfaces a useful retry message later.
            }
        });
    }

    public void lookup(String rawWord, Callback callback) {
        final String query = WordUtils.normalizeWord(rawWord);
        if (query.isEmpty()) {
            mainHandler.post(() -> callback.onSuccess(WordEntry.notFound(rawWord)));
            return;
        }
        final WordEntry cached = cache.get(query);
        if (cached != null) {
            mainHandler.post(() -> callback.onSuccess(cached));
            return;
        }
        executor.execute(() -> {
            try {
                ensureDatabase();
                WordEntry entry = lookupSync(query);
                cache.put(query, entry);
                mainHandler.post(() -> callback.onSuccess(entry));
            } catch (Exception exception) {
                mainHandler.post(callback::onError);
            }
        });
    }

    public void lookupAfterRetry(String rawWord, Callback callback) {
        final String query = WordUtils.normalizeWord(rawWord);
        executor.execute(() -> {
            synchronized (this) {
                prepareError = null;
                if (database != null) {
                    database.close();
                    database = null;
                }
                cache.evictAll();
            }
            try {
                ensureDatabase();
                WordEntry entry = lookupSync(query);
                cache.put(query, entry);
                mainHandler.post(() -> callback.onSuccess(entry));
            } catch (Exception exception) {
                mainHandler.post(callback::onError);
            }
        });
    }

    private synchronized void ensureDatabase() throws IOException {
        if (database != null && database.isOpen()) {
            return;
        }
        if (prepareError != null) {
            throw prepareError;
        }
        File dbFile = new File(context.getFilesDir(), DB_FILE_NAME);
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        boolean current = dbFile.exists()
                && dbFile.length() >= MIN_VALID_SIZE
                && prefs.getInt(KEY_ASSET_VERSION, -1) == ASSET_VERSION;
        try {
            if (!current) {
                copyAsset(dbFile);
                prefs.edit().putInt(KEY_ASSET_VERSION, ASSET_VERSION).apply();
            }
            database = SQLiteDatabase.openDatabase(
                    dbFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
        } catch (IOException | RuntimeException exception) {
            prepareError = new IOException("Could not prepare dictionary", exception);
            throw prepareError;
        }
    }

    private void copyAsset(File destination) throws IOException {
        File temp = new File(destination.getParentFile(), DB_FILE_NAME + ".tmp");
        if (temp.exists() && !temp.delete()) {
            throw new IOException("Could not remove old dictionary file");
        }
        destination.getParentFile().mkdirs();
        byte[] buffer = new byte[128 * 1024];
        try (InputStream input = context.getAssets().open(ASSET_NAME);
             OutputStream output = new FileOutputStream(temp)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            output.flush();
        }
        if (temp.length() < MIN_VALID_SIZE) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            throw new IOException("Dictionary asset is incomplete");
        }
        if (destination.exists() && !destination.delete()) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            throw new IOException("Could not replace dictionary file");
        }
        if (!temp.renameTo(destination)) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            throw new IOException("Could not install dictionary file");
        }
    }

    private WordEntry lookupSync(String query) {
        SQLiteDatabase db = database;
        if (db == null || !db.isOpen()) {
            return WordEntry.notFound(query);
        }
        WordEntry entry = queryExact(db, query);
        if (entry != null) {
            return entry;
        }

        List<String> candidates = new ArrayList<>();
        if (query.endsWith("'s")) {
            candidates.add(query.substring(0, query.length() - 2));
        } else if (query.endsWith("s'")) {
            candidates.add(query.substring(0, query.length() - 1));
        }
        candidates.addAll(WordUtils.baseFormCandidates(query));

        for (String candidate : candidates) {
            if (candidate == null || candidate.isEmpty()) {
                continue;
            }
            entry = queryExact(db, candidate);
            if (entry != null) {
                return entry;
            }
        }

        // A few ECDICT entries keep a typographic apostrophe.
        if (query.contains("'")) {
            entry = queryExact(db, query.replace('\'', '\u2019'));
            if (entry != null) {
                return entry;
            }
        }

        // Loanwords are often typed with accents (café, naïve, résumé) while
        // the dictionary may store the ASCII spelling.
        String accentless = Normalizer.normalize(query, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        if (!accentless.equals(query)) {
            entry = queryExact(db, accentless);
            if (entry != null) {
                return entry;
            }
        }
        return WordEntry.notFound(query);
    }

    private WordEntry queryExact(SQLiteDatabase db, String word) {
        try (Cursor cursor = db.rawQuery(
                "SELECT word, phonetic, definition, translation, tag, collins, "
                        + "oxford, bnc, frq, exchange FROM entries "
                        + "WHERE word = ? COLLATE NOCASE LIMIT 1",
                new String[]{word})) {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return new WordEntry(
                    true,
                    safeString(cursor, 0),
                    safeString(cursor, 1),
                    safeString(cursor, 2),
                    safeString(cursor, 3),
                    safeString(cursor, 4),
                    cursor.getInt(5),
                    cursor.getInt(6),
                    cursor.getInt(7),
                    cursor.getInt(8),
                    safeString(cursor, 9));
        }
    }

    private static String safeString(Cursor cursor, int index) {
        String value = cursor.getString(index);
        return value == null ? "" : value;
    }
}
