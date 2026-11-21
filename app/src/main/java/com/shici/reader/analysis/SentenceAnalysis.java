package com.shici.reader.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The four parts requested for every sentence: the original sentence, a
 * word/phrase breakdown, a Chinese translation and grammar/usage notes.
 */
public final class SentenceAnalysis {

    public final String sentence;
    public final List<BreakdownItem> breakdown;
    public final String translation;
    public final List<String> tips;

    public SentenceAnalysis(String sentence, List<BreakdownItem> breakdown,
                            String translation, List<String> tips) {
        this.sentence = sentence == null ? "" : sentence;
        this.breakdown = Collections.unmodifiableList(
                breakdown == null ? new ArrayList<>() : new ArrayList<>(breakdown));
        this.translation = translation == null ? "" : translation;
        this.tips = Collections.unmodifiableList(
                tips == null ? new ArrayList<>() : new ArrayList<>(tips));
    }

    public boolean hasBreakdown() {
        return !breakdown.isEmpty();
    }

    public boolean hasTranslation() {
        return !translation.trim().isEmpty();
    }

    public boolean hasTips() {
        return !tips.isEmpty();
    }
}
