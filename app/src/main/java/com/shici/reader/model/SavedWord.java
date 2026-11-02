package com.shici.reader.model;

public final class SavedWord {
    public final long id;
    public final String word;
    public final String phonetic;
    public final String translation;
    public final String context;
    public final String sourceTitle;
    public final long createdAt;

    public SavedWord(long id, String word, String phonetic, String translation,
                     String context, String sourceTitle, long createdAt) {
        this.id = id;
        this.word = word;
        this.phonetic = phonetic;
        this.translation = translation;
        this.context = context;
        this.sourceTitle = sourceTitle;
        this.createdAt = createdAt;
    }
}
