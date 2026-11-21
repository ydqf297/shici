package com.shici.reader.analysis;

import com.shici.reader.util.WordUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A parsed sentence-analysis article. The source passage is kept separately
 * from the sentence cards so the shelf can show a clean preview.
 */
public final class AnalysisDocument {

    public final String source;
    public final String titleHint;
    public final List<SentenceAnalysis> sentences;

    public AnalysisDocument(String source, String titleHint,
                            List<SentenceAnalysis> sentences) {
        this.source = source == null ? "" : source;
        this.titleHint = titleHint == null ? "" : titleHint.trim();
        this.sentences = Collections.unmodifiableList(
                sentences == null ? new ArrayList<>() : new ArrayList<>(sentences));
    }

    public int wordCount() {
        return WordUtils.countWords(source);
    }

    public int sentenceCount() {
        return sentences.size();
    }

    public String suggestedTitle() {
        if (!titleHint.isEmpty()) {
            return titleHint;
        }
        return WordUtils.autoTitle(source);
    }
}
