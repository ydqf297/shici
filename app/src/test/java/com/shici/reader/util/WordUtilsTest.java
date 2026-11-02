package com.shici.reader.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class WordUtilsTest {

    @Test
    public void countsLatinWordsIncludingContractionsAndAccents() {
        assertEquals(4, WordUtils.countWords("Don't stop — well-known café."));
        assertEquals(0, WordUtils.countWords("1234 — !!!"));
        assertEquals(3, WordUtils.countWords("one\ntwo\tthree"));
    }

    @Test
    public void normalizesQuotesAndEdgePunctuation() {
        assertEquals("hello", WordUtils.normalizeWord("  \"Hello,\"  "));
        assertEquals("it's", WordUtils.normalizeWord("It’s"));
        assertEquals("company's", WordUtils.normalizeWord("Company’s"));
        assertEquals("dogs", WordUtils.normalizeWord("dogs'"));
        assertEquals("well-known", WordUtils.normalizeWord("Well-Known"));
    }

    @Test
    public void createsAReadableTitle() {
        assertEquals("The Quiet Power of Small Steps",
                WordUtils.autoTitle("The Quiet Power of Small Steps\n\nWe often imagine."));
        assertEquals("We often imagine that progress arrives in a\u2026",
                WordUtils.autoTitle(
                        "We often imagine that progress arrives in a single, dramatic moment."));
        assertEquals("", WordUtils.autoTitle("   \n   "));
    }

    @Test
    public void extractsTheClickedSentenceWithoutDecoration() {
        String text = "First sentence. Second one has a word here! Third.";
        int start = text.indexOf("word");
        String context = WordUtils.contextAround(text, start, start + "word".length());
        assertEquals("Second one has a word here!", context);
    }

    @Test
    public void convertsEcdictPhoneticsToReadableIpa() {
        assertEquals("\u02c8djur\u0259bl", WordUtils.prettyPhonetic("'djurәbl"));
        assertEquals("", WordUtils.prettyPhonetic(""));
    }

    @Test
    public void suggestsCommonBaseForms() {
        List<String> running = WordUtils.baseFormCandidates("running");
        assertTrue(running.contains("run"));
        assertTrue(WordUtils.baseFormCandidates("studies").contains("study"));
        assertTrue(WordUtils.baseFormCandidates("stopped").contains("stop"));
        assertTrue(WordUtils.baseFormCandidates("cities").contains("city"));
        assertTrue(WordUtils.baseFormCandidates("better").contains("bett"));
        assertFalse(WordUtils.baseFormCandidates("cat").contains("ca"));
    }

    @Test
    public void splitsTranslationsAndDefinitions() {
        List<String[]> translations = WordUtils.splitTranslations("n. 苹果\nv. 吃\n[医] 苹果");
        assertEquals(3, translations.size());
        assertEquals("n", translations.get(0)[0]);
        assertEquals("苹果", translations.get(0)[1]);
        assertEquals("", translations.get(2)[0]);

        List<String[]> definitions = WordUtils.splitDefinitions(
                "n. a fruit\nv. to eat\nThis is a large building");
        assertEquals(3, definitions.size());
        assertEquals("n", definitions.get(0)[0]);
        assertEquals("v", definitions.get(1)[0]);
        assertEquals("", definitions.get(2)[0]);
    }
}
