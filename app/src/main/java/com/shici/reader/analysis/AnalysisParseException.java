package com.shici.reader.analysis;

/**
 * Raised when imported text does not contain the minimum structure needed for
 * a sentence-by-sentence breakdown: an English passage and at least one
 * numbered sentence with its explanations.
 */
public final class AnalysisParseException extends Exception {

    public AnalysisParseException(String message) {
        super(message);
    }
}
