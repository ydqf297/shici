package com.shici.reader.model;

import com.shici.reader.util.WordUtils;

public final class Article {

    public static final int KIND_READING = 0;
    public static final int KIND_ANALYSIS = 1;

    public final long id;
    public final String title;
    public final String body;
    public final long createdAt;
    public final boolean sample;
    public final int kind;
    public final String sourceText;
    public final int wordCount;

    public Article(long id, String title, String body, long createdAt, boolean sample,
                   int kind, String sourceText, int wordCount) {
        this.id = id;
        this.title = title;
        this.body = body;
        this.createdAt = createdAt;
        this.sample = sample;
        this.kind = kind;
        this.sourceText = sourceText == null ? "" : sourceText;
        this.wordCount = wordCount;
    }

    public boolean isAnalysis() {
        return kind == KIND_ANALYSIS;
    }

    /**
     * The clean English passage for cards and word counts. Older rows created
     * before schema v2 have no separate source column, so fall back to body.
     */
    public String displaySource() {
        return sourceText.trim().isEmpty() ? body : sourceText;
    }

    public int effectiveWordCount() {
        return wordCount > 0 ? wordCount : WordUtils.countWords(displaySource());
    }
}
