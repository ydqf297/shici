package com.shici.reader.model;

public final class Article {
    public final long id;
    public final String title;
    public final String body;
    public final long createdAt;
    public final boolean sample;

    public Article(long id, String title, String body, long createdAt, boolean sample) {
        this.id = id;
        this.title = title;
        this.body = body;
        this.createdAt = createdAt;
        this.sample = sample;
    }
}
