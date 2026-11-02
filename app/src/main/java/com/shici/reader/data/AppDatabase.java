package com.shici.reader.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.NonNull;

import com.shici.reader.SampleContent;
import com.shici.reader.model.Article;
import com.shici.reader.model.SavedWord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Small local store for the shelf and the wordbook. All methods do synchronous
 * I/O and must be called from a worker thread.
 */
public final class AppDatabase extends SQLiteOpenHelper {

    private static final String DB_NAME = "shici.db";
    private static final int DB_VERSION = 1;

    private static volatile AppDatabase instance;

    public static AppDatabase get(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = new AppDatabase(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private AppDatabase(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onConfigure(@NonNull SQLiteDatabase db) {
        super.onConfigure(db);
        setWriteAheadLoggingEnabled(true);
    }

    @Override
    public void onCreate(@NonNull SQLiteDatabase db) {
        db.execSQL("CREATE TABLE articles ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "title TEXT NOT NULL,"
                + "body TEXT NOT NULL,"
                + "created_at INTEGER NOT NULL,"
                + "is_sample INTEGER NOT NULL DEFAULT 0)");

        db.execSQL("CREATE TABLE saved_words ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "word TEXT NOT NULL COLLATE NOCASE UNIQUE,"
                + "phonetic TEXT NOT NULL DEFAULT '',"
                + "translation TEXT NOT NULL DEFAULT '',"
                + "context TEXT NOT NULL DEFAULT '',"
                + "source_title TEXT NOT NULL DEFAULT '',"
                + "created_at INTEGER NOT NULL)");

        db.execSQL("CREATE INDEX idx_saved_words_created ON saved_words(created_at DESC)");

        // A quiet first read makes the empty state useful without hiding the
        // import flow. It can be removed like any other article.
        ContentValues values = new ContentValues();
        values.put("title", SampleContent.TITLE);
        values.put("body", SampleContent.BODY);
        values.put("created_at", System.currentTimeMillis() - 60_000L);
        values.put("is_sample", 1);
        db.insert("articles", null, values);
    }

    @Override
    public void onUpgrade(@NonNull SQLiteDatabase db, int oldVersion, int newVersion) {
        // Version 1 is the initial schema. Future migrations belong here.
    }

    public List<Article> getArticles() {
        List<Article> result = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.rawQuery(
                "SELECT id, title, body, created_at, is_sample FROM articles "
                        + "ORDER BY created_at DESC, id DESC", null)) {
            while (cursor.moveToNext()) {
                result.add(new Article(
                        cursor.getLong(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getLong(3),
                        cursor.getInt(4) == 1));
            }
        }
        return result;
    }

    public Article getArticle(long id) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.rawQuery(
                "SELECT id, title, body, created_at, is_sample FROM articles WHERE id = ?",
                new String[]{String.valueOf(id)})) {
            if (cursor.moveToFirst()) {
                return new Article(
                        cursor.getLong(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getLong(3),
                        cursor.getInt(4) == 1);
            }
        }
        return null;
    }

    public long insertArticle(String title, String body, boolean sample) {
        ContentValues values = new ContentValues();
        values.put("title", title);
        values.put("body", body);
        values.put("created_at", System.currentTimeMillis());
        values.put("is_sample", sample ? 1 : 0);
        return getWritableDatabase().insertOrThrow("articles", null, values);
    }

    public void deleteArticle(long id) {
        getWritableDatabase().delete("articles", "id = ?", new String[]{String.valueOf(id)});
    }

    public List<SavedWord> getSavedWords() {
        List<SavedWord> result = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.rawQuery(
                "SELECT id, word, phonetic, translation, context, source_title, created_at "
                        + "FROM saved_words ORDER BY created_at DESC, id DESC", null)) {
            while (cursor.moveToNext()) {
                result.add(new SavedWord(
                        cursor.getLong(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getString(3),
                        cursor.getString(4),
                        cursor.getString(5),
                        cursor.getLong(6)));
            }
        }
        return result;
    }

    public int getSavedWordCount() {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM saved_words", null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    public int getArticleCount() {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM articles", null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    public boolean isWordSaved(String word) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.rawQuery(
                "SELECT 1 FROM saved_words WHERE word = ? COLLATE NOCASE LIMIT 1",
                new String[]{word})) {
            return cursor.moveToFirst();
        }
    }

    public long saveWord(String word, String phonetic, String translation,
                         String context, String sourceTitle) {
        ContentValues values = new ContentValues();
        values.put("word", word);
        values.put("phonetic", phonetic == null ? "" : phonetic);
        values.put("translation", translation == null ? "" : translation);
        values.put("context", context == null ? "" : context);
        values.put("source_title", sourceTitle == null ? "" : sourceTitle);
        values.put("created_at", System.currentTimeMillis());
        return getWritableDatabase().insertWithOnConflict(
                "saved_words", null, values, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public void removeSavedWord(String word) {
        getWritableDatabase().delete(
                "saved_words", "word = ? COLLATE NOCASE", new String[]{word});
    }

    public void deleteAllSavedWords() {
        getWritableDatabase().delete("saved_words", null, null);
    }

    public static <T> List<T> emptyIfNull(List<T> list) {
        return list == null ? Collections.emptyList() : list;
    }
}
