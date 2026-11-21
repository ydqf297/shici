package com.shici.reader.analysis;

/**
 * One word or phrase from a sentence, together with its short Chinese gloss.
 * The gloss is optional so hand-written notes without brackets still render.
 */
public final class BreakdownItem {

    public final String text;
    public final String gloss;

    public BreakdownItem(String text, String gloss) {
        this.text = text == null ? "" : text;
        this.gloss = gloss == null ? "" : gloss;
    }
}
