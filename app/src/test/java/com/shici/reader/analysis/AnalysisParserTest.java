package com.shici.reader.analysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AnalysisParserTest {

    private static final String SAMPLE =
            "### 英语短文 (English Essay)\n\n"
            + "The **development** of modern technology is an **ongoing** process. "
            + "A skilled **developer** usually plays a key role in this "
            + "**developmental** stage. As society **advances**, our lives **evolve** "
            + "rapidly. This **evolution** is not just an **evolutionary** change in "
            + "tools; it is a **revolutionary** shift in how we live.\n\n"
            + "---\n\n"
            + "### 解析 (Sentence-by-Sentence Breakdown)\n\n"
            + "#### 1. The development of modern technology is an ongoing process.\n"
            + "*   **逐词/短语拆解**：\n"
            + "    *   The development (发展，名词)\n"
            + "    *   of modern technology (现代科技的)\n"
            + "    *   is (是)\n"
            + "    *   an ongoing (一个持续的/进行中的)\n"
            + "    *   process (过程).\n"
            + "*   **中文翻译**：现代科技的发展是一个持续的过程。\n"
            + "*   **语法与用法提示**：\n"
            + "    *   `development` 是 `develop` 的名词形式，常与 `of` 连用表示“……的发展”。\n"
            + "    *   `ongoing` 是形容词，常用于描述正在进行、尚未结束的事情。\n\n"
            + "#### 2. A skilled developer usually plays a key role in this developmental stage.\n"
            + "*   **逐词/短语拆解**：\n"
            + "    *   A skilled developer (一位熟练的开发者)\n"
            + "    *   usually (通常)\n"
            + "    *   plays a key role (起关键作用)\n"
            + "    *   in this developmental stage (在这个发展阶段).\n"
            + "*   **中文翻译**：一位熟练的开发者通常在这个发展阶段起着关键作用。\n"
            + "*   **语法与用法提示**：\n"
            + "    *   `developer` 指“开发者”，由 `develop` + `-er` 后缀构成。\n"
            + "    *   `play a key role in...` 是四级高频短语，意为“在……中起关键作用”。\n";

    @Test
    public void parsesTheDocumentedFormat() throws Exception {
        AnalysisDocument document = AnalysisParser.parse(SAMPLE);

        assertTrue(document.source.startsWith("The development of modern technology"));
        assertFalse(document.source.contains("**"));
        assertTrue(document.source.contains("revolutionary"));
        assertEquals(2, document.sentenceCount());

        SentenceAnalysis first = document.sentences.get(0);
        assertEquals("The development of modern technology is an ongoing process.",
                first.sentence);
        assertEquals(5, first.breakdown.size());
        assertEquals("The development", first.breakdown.get(0).text);
        assertEquals("发展，名词", first.breakdown.get(0).gloss);
        assertEquals("现代科技的发展是一个持续的过程。", first.translation);
        assertEquals(2, first.tips.size());
        assertTrue(first.tips.get(0).contains("`development`"));

        SentenceAnalysis second = document.sentences.get(1);
        assertEquals(4, second.breakdown.size());
        assertEquals("A skilled developer", second.breakdown.get(0).text);
        assertEquals("一位熟练的开发者", second.breakdown.get(0).gloss);
        assertTrue(second.translation.startsWith("一位熟练的开发者"));
        assertEquals(2, second.tips.size());
    }

    @Test
    public void acceptsFullWidthColonsAndBoldLabelsWithoutColons() throws Exception {
        String text =
                "# A Tiny Title\n\n"
                + "### English Essay\n\n"
                + "A small test sentence.\n\n"
                + "### 解析\n\n"
                + "#### 1. A small test sentence.\n"
                + "**逐词拆解**\n"
                + "- A small (小的)\n"
                + "- test (测试)\n"
                + "**中文翻译**：一个小小的测试句子。\n"
                + "**语法提示**：`small` 是形容词。\n";
        AnalysisDocument document = AnalysisParser.parse(text);
        assertEquals("A Tiny Title", document.titleHint);
        assertEquals("A small test sentence.", document.source);
        assertEquals(1, document.sentenceCount());
        assertEquals(2, document.sentences.get(0).breakdown.size());
        assertEquals("一个小小的测试句子。", document.sentences.get(0).translation);
        assertEquals("`small` 是形容词。", document.sentences.get(0).tips.get(0));
    }

    @Test
    public void acceptsSentenceNumberOnItsOwnLine() throws Exception {
        String text =
                "### 英语短文\nA single sentence for testing.\n"
                + "### 解析\n"
                + "#### 1\nA single sentence for testing.\n"
                + "**中文翻译**：一个用于测试的句子。\n";
        AnalysisDocument document = AnalysisParser.parse(text);
        assertEquals(1, document.sentenceCount());
        assertEquals("A single sentence for testing.", document.sentences.get(0).sentence);
        assertEquals("一个用于测试的句子。", document.sentences.get(0).translation);
    }

    @Test
    public void extractsOnlyTheChineseGlossFromMultipleParentheses() throws Exception {
        String text =
                "### 英语短文\nWe process data quickly.\n"
                + "### 解析\n"
                + "#### 1. We process data quickly.\n"
                + "* **逐词/短语拆解**：\n"
                + "    * process (过程) and data (English note)\n"
                + "* **中文翻译**：我们快速处理数据。\n";
        AnalysisDocument document = AnalysisParser.parse(text);
        BreakdownItem item = document.sentences.get(0).breakdown.get(0);
        assertEquals("process", item.text);
        assertEquals("过程", item.gloss);
    }

    @Test
    public void keepsTipLinesThatContainAColon() throws Exception {        String text =
                "### 英语短文\nA grammar note is useful.\n"
                + "### 解析\n"
                + "#### 1. A grammar note is useful.\n"
                + "* **中文翻译**：一条语法笔记很有用。\n"
                + "* **语法与用法提示**：\n"
                + "    * `note` 的用法：作名词时表示“笔记”。\n"
                + "    * 语法上：`useful` 是形容词。\n";
        AnalysisDocument document = AnalysisParser.parse(text);
        SentenceAnalysis sentence = document.sentences.get(0);
        assertEquals(2, sentence.tips.size());
        assertEquals("`note` 的用法：作名词时表示“笔记”。", sentence.tips.get(0));
    }

    @Test
    public void detectsTheFormatAndRejectsPlainEssays() {
        assertTrue(AnalysisParser.looksLikeAnalysis(SAMPLE));
        assertFalse(AnalysisParser.looksLikeAnalysis(
                "This is a normal English essay.\n\nIt has no breakdown."));
        assertFalse(AnalysisParser.looksLikeAnalysis(""));
    }

    @Test
    public void rejectsContentWithoutSentences() {
        assertThrows(AnalysisParseException.class,
                () -> AnalysisParser.parse("### 英语短文\nA passage without an analysis."));
        assertThrows(AnalysisParseException.class,
                () -> AnalysisParser.parse("### 解析\n#### 1. A sentence.\n**中文翻译**：一个句子。"));
    }

    @Test
    public void createsAFallbackTitleFromThePassage() throws Exception {
        AnalysisDocument document = AnalysisParser.parse(SAMPLE);
        assertFalse(document.suggestedTitle().isEmpty());
        assertTrue(document.suggestedTitle().startsWith("The development"));
    }
}
