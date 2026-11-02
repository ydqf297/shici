package com.shici.reader.model;

public final class WordEntry {
    public final boolean found;
    public final String word;
    public final String phonetic;
    public final String definition;
    public final String translation;
    public final String tag;
    public final int collins;
    public final int oxford;
    public final int bnc;
    public final int frq;
    public final String exchange;

    public WordEntry(boolean found, String word, String phonetic, String definition,
                     String translation, String tag, int collins, int oxford,
                     int bnc, int frq, String exchange) {
        this.found = found;
        this.word = word;
        this.phonetic = phonetic;
        this.definition = definition;
        this.translation = translation;
        this.tag = tag;
        this.collins = collins;
        this.oxford = oxford;
        this.bnc = bnc;
        this.frq = frq;
        this.exchange = exchange;
    }

    public static WordEntry notFound(String query) {
        return new WordEntry(false, query, "", "", "", "", 0, 0, 0, 0, "");
    }
}
