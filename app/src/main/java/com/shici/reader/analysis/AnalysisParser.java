package com.shici.reader.analysis;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the Markdown-ish text format used by the sentence-analysis importer:
 *
 * <pre>
 * ### 英语短文
 * The development of modern technology is an ongoing process.
 *
 * ### 解析
 * #### 1. The development of modern technology is an ongoing process.
 * * **逐词/短语拆解**：
 *     * The development (发展，名词)
 * * **中文翻译**：现代科技的发展是一个持续的过程。
 * * **语法与用法提示**：
 *     * `development` 是 develop 的名词形式。
 * </pre>
 *
 * The parser is deliberately forgiving about heading levels, bullet styles and
 * full-width punctuation, while still requiring both a passage and numbered
 * sentences so a malformed paste never becomes an empty analysis screen.
 */
public final class AnalysisParser {

    private static final Pattern HEADING =
            Pattern.compile("^\\s{0,3}(#{1,6})\\s+(.*?)\\s*#*\\s*$");
    private static final Pattern HORIZONTAL_RULE =
            Pattern.compile("^\\s*(?:-{3,}|\\*{3,}|_{3,})\\s*$");
    private static final Pattern NUMBERED_SENTENCE = Pattern.compile(
            "^\\s*(?:sentence\\s*)?(\\d{1,3})\\s*[.、:：)）\\]]\\s*(.*)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMBER_ONLY = Pattern.compile(
            "^\\s*(?:sentence\\s*)?(\\d{1,3})\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern LIST_MARKER = Pattern.compile(
            "^\\s*(?:[-+•·]\\s+|\\*\\s+|\\d{1,2}[.)、]\\s+)");
    private static final Pattern INLINE_MARKDOWN = Pattern.compile(
            "!?\\[([^\\]]*)]\\([^)]*\\)|<[^>]+>");
    private static final Pattern EMPHASIS = Pattern.compile(
            "(?<!\\*)\\*([^*\\n]+)\\*(?!\\*)");

    private AnalysisParser() {
    }

    public static boolean looksLikeAnalysis(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return false;
        }
        String lower = raw.toLowerCase(Locale.ROOT);
        boolean hasAnalysisMarker = raw.contains("解析") || lower.contains("breakdown");
        boolean hasStructure = raw.contains("####")
                || raw.contains("**逐词")
                || raw.contains("中文翻译")
                || lower.contains("translation");
        return hasAnalysisMarker && hasStructure;
    }

    public static AnalysisDocument parse(String raw) throws AnalysisParseException {
        List<String> lines = normalizeLines(raw);
        if (lines.isEmpty()) {
            throw new AnalysisParseException("Empty content");
        }

        int sourceHeading = -1;
        int analysisHeading = -1;
        for (int i = 0; i < lines.size(); i++) {
            String candidate = sectionCandidate(lines.get(i));
            if (candidate == null) {
                continue;
            }
            if (sourceHeading < 0 && isSourceSection(candidate)) {
                sourceHeading = i;
            } else if (analysisHeading < 0 && isAnalysisSection(candidate)) {
                analysisHeading = i;
            }
        }

        int analysisStart;
        if (analysisHeading >= 0) {
            analysisStart = analysisHeading;
        } else {
            List<SentenceStart> probe = findSentenceStarts(lines, 0);
            if (probe.isEmpty()) {
                throw new AnalysisParseException("No numbered sentences or analysis heading");
            }
            analysisStart = probe.get(0).lineIndex;
        }

        int sourceEnd = analysisStart;
        if (sourceEnd <= 0) {
            throw new AnalysisParseException("Missing English passage");
        }
        int sourceStart = 0;
        if (sourceHeading >= 0 && sourceHeading < sourceEnd) {
            sourceStart = sourceHeading + 1;
        }
        String source = extractSource(lines, sourceStart, sourceEnd);
        if (source.isEmpty()) {
            throw new AnalysisParseException("Missing English passage");
        }

        List<SentenceStart> starts = findSentenceStarts(lines, analysisStart + 1);
        if (starts.isEmpty()) {
            throw new AnalysisParseException("No numbered sentences");
        }

        List<SentenceAnalysis> sentences = new ArrayList<>();
        for (int i = 0; i < starts.size(); i++) {
            SentenceStart start = starts.get(i);
            int blockEnd = i + 1 < starts.size()
                    ? starts.get(i + 1).lineIndex : lines.size();
            sentences.add(parseSentenceBlock(lines, start, blockEnd));
        }

        boolean hasDetail = false;
        for (SentenceAnalysis sentence : sentences) {
            if (sentence.hasBreakdown() || sentence.hasTranslation() || sentence.hasTips()) {
                hasDetail = true;
                break;
            }
        }
        if (!hasDetail) {
            throw new AnalysisParseException("Sentence details are missing");
        }

        String titleHint = findTitleHint(lines, sourceStart);
        return new AnalysisDocument(source, titleHint, sentences);
    }

    private static List<String> normalizeLines(String raw) {
        String text = raw == null ? "" : Normalizer.normalize(raw, Normalizer.Form.NFC);
        text = text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\u00A0', ' ')
                .replace("\uFEFF", "");
        String[] split = text.split("\n", -1);
        int start = 0;
        int end = split.length;
        if (split.length >= 2 && split[0].trim().startsWith("```")
                && split[split.length - 1].trim().equals("```")) {
            start = 1;
            end = split.length - 1;
        }
        List<String> lines = new ArrayList<>();
        for (int i = start; i < end; i++) {
            lines.add(split[i]);
        }
        return lines;
    }

    private static String extractSource(List<String> lines, int start, int end) {
        List<String> paragraphs = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = start; i < end; i++) {
            String raw = lines.get(i);
            if (raw.trim().isEmpty() || HORIZONTAL_RULE.matcher(raw).matches()) {
                flushParagraph(paragraphs, current);
                continue;
            }
            String section = sectionCandidate(raw);
            if (section != null && (isSourceSection(section) || isAnalysisSection(section))) {
                flushParagraph(paragraphs, current);
                continue;
            }
            if (heading(raw) != null) {
                // A title or sub-heading belongs to the page chrome, not the
                // passage itself. Skipping it also avoids duplicate titles.
                flushParagraph(paragraphs, current);
                continue;
            }
            String line = stripInlineMarkdown(stripListMarker(stripBlockquote(raw)));
            if (line.isEmpty()) {
                continue;
            }
            if (current.length() > 0) {
                current.append(' ');
            }
            current.append(line);
        }
        flushParagraph(paragraphs, current);
        return join(paragraphs, "\n\n");
    }

    private static void flushParagraph(List<String> paragraphs, StringBuilder current) {
        String text = current.toString().trim();
        if (!text.isEmpty()) {
            paragraphs.add(text);
        }
        current.setLength(0);
    }

    private static String findTitleHint(List<String> lines, int end) {
        for (int i = 0; i < end; i++) {
            Heading heading = heading(lines.get(i));
            if (heading == null || heading.level > 2) {
                continue;
            }
            String text = stripInlineMarkdown(heading.text);
            if (!text.isEmpty() && !isSourceSection(text) && !isAnalysisSection(text)) {
                return text;
            }
        }
        return "";
    }

    private static List<SentenceStart> findSentenceStarts(List<String> lines, int from) {
        List<SentenceStart> result = new ArrayList<>();
        for (int i = Math.max(0, from); i < lines.size(); i++) {
            String raw = lines.get(i);
            Heading heading = heading(raw);
            String candidate = null;
            if (heading != null) {
                if (heading.level < 3) {
                    continue;
                }
                candidate = heading.text;
            } else {
                String trimmed = raw.trim();
                if (isBoldOnly(trimmed)) {
                    candidate = stripInlineMarkdown(trimmed);
                } else {
                    String plain = stripInlineMarkdown(stripBlockquote(trimmed));
                    if (NUMBERED_SENTENCE.matcher(plain).matches()
                            && hasLabelNearby(lines, i + 1)) {
                        candidate = plain;
                    }
                }
            }
            if (candidate == null) {
                continue;
            }

            String clean = stripInlineMarkdown(candidate).trim();
            Matcher numbered = NUMBERED_SENTENCE.matcher(clean);
            Matcher numberOnly = NUMBER_ONLY.matcher(clean);
            String sentence;
            if (numbered.matches()) {
                sentence = numbered.group(2).trim();
            } else if (numberOnly.matches()) {
                sentence = "";
            } else {
                continue;
            }

            int bodyStart = i + 1;
            if (sentence.isEmpty()) {
                int next = i + 1;
                while (next < lines.size() && lines.get(next).trim().isEmpty()) {
                    next++;
                }
                if (next < lines.size() && !HORIZONTAL_RULE.matcher(lines.get(next)).matches()
                        && !isLabelLine(lines.get(next))) {
                    sentence = stripInlineMarkdown(stripBlockquote(lines.get(next))).trim();
                    bodyStart = next + 1;
                }
            }
            if (sentence.isEmpty()) {
                continue;
            }
            result.add(new SentenceStart(i, bodyStart, sentence));
        }
        return result;
    }

    private static boolean hasLabelNearby(List<String> lines, int from) {
        int limit = Math.min(lines.size(), from + 24);
        for (int i = from; i < limit; i++) {
            if (isLabelLine(lines.get(i))) {
                return true;
            }
        }
        return false;
    }

    private static SentenceAnalysis parseSentenceBlock(
            List<String> lines, SentenceStart start, int end) {
        List<BreakdownItem> breakdown = new ArrayList<>();
        List<String> tips = new ArrayList<>();
        StringBuilder translation = new StringBuilder();
        List<String> buffer = new ArrayList<>();
        LabelType current = null;

        for (int i = start.bodyStart; i < end; i++) {
            String raw = lines.get(i);
            if (raw.trim().isEmpty() || HORIZONTAL_RULE.matcher(raw).matches()) {
                continue;
            }
            LabelMatch label = labelOf(raw);
            if (label != null) {
                appendLabelContent(current, buffer, breakdown, translation, tips);
                buffer.clear();
                current = label.type;
                String inline = label.inline == null ? "" : label.inline.trim();
                if (!inline.isEmpty()) {
                    buffer.add(inline);
                }
                continue;
            }
            if (current == null || heading(raw) != null) {
                continue;
            }
            String content = stripBlockquote(raw).trim();
            if (!content.isEmpty()) {
                buffer.add(content);
            }
        }
        appendLabelContent(current, buffer, breakdown, translation, tips);
        return new SentenceAnalysis(start.sentence, breakdown,
                translation.toString().trim(), tips);
    }

    private static void appendLabelContent(LabelType type, List<String> buffer,
                                           List<BreakdownItem> breakdown,
                                           StringBuilder translation, List<String> tips) {
        if (type == null || buffer.isEmpty()) {
            return;
        }
        switch (type) {
            case BREAKDOWN:
                for (String line : buffer) {
                    BreakdownItem item = parseBreakdownItem(line);
                    if (item != null) {
                        breakdown.add(item);
                    }
                }
                break;
            case TRANSLATION:
                List<String> translationLines = new ArrayList<>();
                for (String line : buffer) {
                    String clean = stripListMarker(stripBlockquote(line)).trim();
                    if (!clean.isEmpty()) {
                        translationLines.add(clean);
                    }
                }
                String text = join(translationLines, " ");
                if (!text.isEmpty()) {
                    if (translation.length() > 0) {
                        translation.append(' ');
                    }
                    translation.append(text);
                }
                break;
            case TIPS:
                parseTips(buffer, tips);
                break;
            default:
                break;
        }
    }

    private static BreakdownItem parseBreakdownItem(String raw) {
        String line = stripInlineMarkdown(stripListMarker(raw)).trim();
        if (line.isEmpty()) {
            return null;
        }

        String text = line;
        String gloss = "";
        int[] parentheses = chineseParenthetical(line);
        if (parentheses != null) {
            text = line.substring(0, parentheses[0]).trim();
            gloss = line.substring(parentheses[0] + 1, parentheses[1]).trim();
        }
        if (gloss.isEmpty()) {
            int dash = lastDashSeparator(line);
            if (dash > 0) {
                String tail = line.substring(dash).replaceFirst("^[\\s—–\\-]+", "").trim();
                if (containsChinese(tail)) {
                    text = line.substring(0, dash).trim();
                    gloss = tail;
                }
            }
        }
        text = stripInlineMarkdown(text).replaceAll("\\s+", " ").trim();
        gloss = stripInlineMarkdown(gloss).replaceAll("\\s+", " ").trim();
        if (text.isEmpty() && gloss.isEmpty()) {
            return null;
        }
        return new BreakdownItem(text, gloss);
    }

    private static void parseTips(List<String> buffer, List<String> tips) {
        boolean appendedContinuation = false;
        for (String raw : buffer) {
            boolean bullet = hasListMarker(raw);
            String content = stripListMarker(stripBlockquote(raw)).trim();
            if (content.isEmpty()) {
                continue;
            }
            if (tips.isEmpty() || bullet || !appendedContinuation) {
                tips.add(content);
            } else {
                int last = tips.size() - 1;
                tips.set(last, tips.get(last) + " " + content);
            }
            appendedContinuation = !bullet;
        }
    }

    private static LabelMatch labelOf(String raw) {
        String line = stripListMarker(raw).trim();
        if (line.isEmpty()) {
            return null;
        }
        String plain = stripInlineMarkdown(line).trim();
        int colon = firstColon(plain);
        String head = colon >= 0 ? plain.substring(0, colon).trim() : plain;
        if (head.isEmpty() || head.length() > 32) {
            return null;
        }
        String key = head.replaceAll("[\\s*_`]", "")
                .toLowerCase(Locale.ROOT);
        LabelType type = labelType(key, colon >= 0);
        if (type == null) {
            return null;
        }
        int rawColon = firstColon(line);
        String inline = rawColon >= 0
                ? line.substring(Math.min(line.length(), rawColon + 1)).trim() : "";
        return new LabelMatch(type, inline);
    }

    private static boolean isLabelLine(String raw) {
        return labelOf(raw) != null;
    }

    private static LabelType labelType(String key, boolean hasColon) {
        if (key.equals("逐词/短语拆解") || key.equals("逐词／短语拆解")
                || key.equals("逐词拆解") || key.equals("短语拆解")
                || key.equals("词句拆解") || key.equals("拆解")
                || key.startsWith("breakdown") || key.equals("wordbreakdown")) {
            return LabelType.BREAKDOWN;
        }
        if (key.equals("中文翻译") || key.equals("翻译") || key.equals("译文")
                || key.equals("translation") || key.equals("chinesetranslation")) {
            return LabelType.TRANSLATION;
        }
        if (key.equals("语法与用法提示") || key.equals("语法提示")
                || key.equals("用法提示") || key.equals("语法与用法")
                || key.equals("语法讲解") || key.equals("grammarandusage")
                || key.equals("grammarusagenotes") || key.equals("usagenotes")
                || key.equals("grammarnotes") || key.equals("notes")
                || key.equals("grammar") || key.equals("usage")) {
            return LabelType.TIPS;
        }
        if (!hasColon) {
            return null;
        }
        if (key.contains("拆解")
                && (key.startsWith("逐词") || key.startsWith("短语")
                || key.startsWith("词句") || key.startsWith("拆解")
                || key.startsWith("wordbreakdown") || key.startsWith("breakdown"))) {
            return LabelType.BREAKDOWN;
        }
        if ((key.contains("翻译") || key.contains("译文"))
                && (key.startsWith("中文翻译") || key.startsWith("翻译")
                || key.startsWith("译文") || key.startsWith("translation"))) {
            return LabelType.TRANSLATION;
        }
        if ((key.contains("语法") || key.contains("用法")
                || key.startsWith("grammar") || key.startsWith("usage"))
                && (key.startsWith("语法") || key.startsWith("用法")
                || key.startsWith("grammar") || key.startsWith("usage")
                || key.endsWith("提示") || key.endsWith("说明")
                || key.endsWith("讲解"))) {
            return LabelType.TIPS;
        }
        return null;
    }

    private static String sectionCandidate(String raw) {
        Heading heading = heading(raw);
        if (heading != null) {
            return stripInlineMarkdown(heading.text);
        }
        String trimmed = stripBlockquote(raw).trim();
        if (isBoldOnly(trimmed)) {
            return stripInlineMarkdown(trimmed);
        }
        String plain = stripInlineMarkdown(trimmed);
        String normalized = normalizeHeading(plain);
        if (normalized.length() <= 40
                && (normalized.equals("解析") || normalized.equals("逐句解析")
                || normalized.equals("句子解析") || normalized.equals("英语短文")
                || normalized.equals("英文短文") || normalized.equals("英文原文")
                || normalized.equals("原文") || normalized.equals("english essay")
                || normalized.equals("sentence-by-sentence breakdown")
                || normalized.equals("sentence breakdown")
                || normalized.equals("breakdown"))) {
            return plain;
        }
        return null;
    }

    private static boolean isSourceSection(String text) {
        String key = normalizeHeading(stripInlineMarkdown(text));
        return key.contains("英语短文") || key.contains("英文短文")
                || key.contains("英文原文") || key.equals("原文")
                || key.matches(".*\\benglish\\s+essay\\b.*")
                || key.equals("essay");
    }

    private static boolean isAnalysisSection(String text) {
        String key = normalizeHeading(stripInlineMarkdown(text));
        if (key.contains("英语短文") || key.contains("英文短文")) {
            return false;
        }
        return key.equals("解析") || key.startsWith("解析")
                || key.contains("逐句解析") || key.contains("句子解析")
                || key.contains("sentence-by-sentence")
                || key.contains("sentence breakdown")
                || key.equals("breakdown") || key.startsWith("breakdown ");
    }

    private static String normalizeHeading(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ")
                .trim().toLowerCase(Locale.ROOT);
    }

    private static Heading heading(String raw) {
        if (raw == null) {
            return null;
        }
        Matcher matcher = HEADING.matcher(raw);
        if (!matcher.matches()) {
            return null;
        }
        return new Heading(matcher.group(1).length(), matcher.group(2).trim());
    }

    private static boolean isBoldOnly(String trimmed) {
        return trimmed.length() > 4
                && ((trimmed.startsWith("**") && trimmed.endsWith("**"))
                || (trimmed.startsWith("__") && trimmed.endsWith("__")));
    }

    private static String stripBlockquote(String raw) {
        String value = raw == null ? "" : raw.trim();
        while (value.startsWith(">")) {
            value = value.substring(1).trim();
        }
        return value;
    }

    private static boolean hasListMarker(String raw) {
        return LIST_MARKER.matcher(stripBlockquote(raw)).find();
    }

    private static String stripListMarker(String raw) {
        return LIST_MARKER.matcher(raw == null ? "" : raw).replaceFirst("").trim();
    }

    private static String stripInlineMarkdown(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.replace("\u00A0", " ")
                .replace("**", "")
                .replace("__", "")
                .replace("`", "");
        text = INLINE_MARKDOWN.matcher(text).replaceAll("$1");
        text = EMPHASIS.matcher(text).replaceAll("$1");
        return text.replaceAll("\\s+", " ").trim();
    }

    /**
     * Returns the first parenthesised group whose contents contain Chinese.
     * Looking for the first Chinese group (rather than the last closing bracket)
     * avoids swallowing later English notes into the gloss.
     */
    private static int[] chineseParenthetical(String value) {
        for (int i = 1; i < value.length() - 1; i++) {
            char open = value.charAt(i);
            if (open != '(' && open != '（') {
                continue;
            }
            char closeChar = open == '(' ? ')' : '）';
            char alternate = open == '(' ? '）' : ')';
            int close = value.indexOf(closeChar, i + 1);
            int alternateClose = value.indexOf(alternate, i + 1);
            if (close < 0 || (alternateClose >= 0 && alternateClose < close)) {
                close = alternateClose;
            }
            if (close <= i) {
                continue;
            }
            String inside = value.substring(i + 1, close);
            if (containsChinese(inside)) {
                return new int[]{i, close};
            }
            i = close;
        }
        return null;
    }

    private static int lastDashSeparator(String value) {
        int best = -1;
        for (String dash : new String[]{"—", "–", " - ", " -- "}) {
            int index = value.lastIndexOf(dash);
            if (index > best) {
                best = index;
            }
        }
        return best;
    }

    private static int firstColon(String value) {
        int ascii = value.indexOf(':');
        int full = value.indexOf('：');
        if (ascii < 0) {
            return full;
        }
        if (full < 0) {
            return ascii;
        }
        return Math.min(ascii, full);
    }

    private static boolean containsChinese(String value) {
        for (int i = 0; i < value.length(); ) {
            int code = value.codePointAt(i);
            if (Character.UnicodeScript.of(code) == Character.UnicodeScript.HAN) {
                return true;
            }
            i += Character.charCount(code);
        }
        return false;
    }

    private static String join(List<String> parts, String separator) {
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.trim().isEmpty()) {
                continue;
            }
            if (result.length() > 0) {
                result.append(separator);
            }
            result.append(part.trim());
        }
        return result.toString();
    }

    private enum LabelType {
        BREAKDOWN,
        TRANSLATION,
        TIPS
    }

    private static final class LabelMatch {
        final LabelType type;
        final String inline;

        LabelMatch(LabelType type, String inline) {
            this.type = type;
            this.inline = inline;
        }
    }

    private static final class Heading {
        final int level;
        final String text;

        Heading(int level, String text) {
            this.level = level;
            this.text = text;
        }
    }

    private static final class SentenceStart {
        final int lineIndex;
        final int bodyStart;
        final String sentence;

        SentenceStart(int lineIndex, int bodyStart, String sentence) {
            this.lineIndex = lineIndex;
            this.bodyStart = bodyStart;
            this.sentence = sentence;
        }
    }
}
