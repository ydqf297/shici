package com.shici.reader.util;

import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Text helpers shared by the reader and the dictionary.
 */
public final class WordUtils {

    /**
     * Latin words, including accented letters. Apostrophes and hyphens are kept
     * inside a word (don't, well-known), but never at either end.
     */
    private static final String LATIN =
            "A-Za-z\u00C0-\u00D6\u00D8-\u00F6\u00F8-\u024F\u1E00-\u1EFF";

    public static final Pattern WORD_PATTERN =
            Pattern.compile("[" + LATIN + "]+(?:['\u2019\\-][" + LATIN + "]+)*");

    private static final Pattern POS_PATTERN =
            Pattern.compile("^([A-Za-z]{1,5})\\.\\s*(.*)$");

    private WordUtils() {
    }

    public static int countWords(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int count = 0;
        Matcher matcher = WORD_PATTERN.matcher(text);
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    public static String cleanBody(String raw) {
        if (raw == null) {
            return "";
        }
        String text = Normalizer.normalize(raw, Normalizer.Form.NFC);
        text = text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\t', ' ')
                .replace('\u00A0', ' ');
        StringBuilder builder = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r' || (c >= 0x20 && c != 0x7F)) {
                builder.append(c);
            }
        }
        text = builder.toString().trim();
        text = text.replaceAll("\\n[ \\u00A0]+", "\n");
        text = text.replaceAll("\\n{3,}", "\n\n");
        text = text.replaceAll("[ ]{3,}", "  ");
        return text;
    }

    public static String autoTitle(String body) {
        if (body == null || body.trim().isEmpty()) {
            return "";
        }
        String trimmed = body.trim();
        int newline = trimmed.indexOf('\n');
        String firstLine = newline >= 0 ? trimmed.substring(0, newline).trim() : trimmed;
        if (!firstLine.isEmpty() && firstLine.length() <= 60
                && countWords(firstLine) <= 10) {
            return firstLine;
        }

        int sentenceEnd = trimmed.length();
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '.' || c == '!' || c == '?') {
                sentenceEnd = i + 1;
                break;
            }
        }
        String candidate = trimmed.substring(0, sentenceEnd).trim();
        Matcher matcher = WORD_PATTERN.matcher(candidate);
        List<String> words = new ArrayList<>();
        while (matcher.find() && words.size() < 9) {
            words.add(matcher.group());
        }
        if (words.isEmpty()) {
            return "";
        }
        StringBuilder title = new StringBuilder();
        for (String word : words) {
            if (title.length() > 0) {
                if (title.length() + word.length() + 1 > 46) {
                    break;
                }
                title.append(' ');
            }
            title.append(word);
        }
        if (title.length() == 0) {
            title.append(words.get(0));
        }
        if (countWords(candidate) > countWords(title.toString())) {
            title.append('\u2026');
        }
        return title.toString();
    }

    public static String titleFromFileName(String name) {
        if (name == null) {
            return "";
        }
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        return name.trim();
    }

    /** Lower-cases and removes edge punctuation, keeping internal ' and -. */
    public static String normalizeWord(String raw) {
        if (raw == null) {
            return "";
        }
        String word = Normalizer.normalize(raw, Normalizer.Form.NFC)
                .replace('\u2019', '\'')
                .replace('\u2018', '\'')
                .replace('\u2010', '-')
                .replace('\u2011', '-')
                .trim()
                .toLowerCase(Locale.ROOT);
        int start = 0;
        int end = word.length();
        while (start < end && !Character.isLetter(word.charAt(start))) {
            start++;
        }
        while (end > start && !Character.isLetter(word.charAt(end - 1))
                && word.charAt(end - 1) != '\'' && word.charAt(end - 1) != '-') {
            end--;
        }
        word = word.substring(start, end);
        while (word.endsWith("'") || word.endsWith("-")) {
            word = word.substring(0, word.length() - 1);
        }
        return word;
    }

    /**
     * Returns one sentence around the word, with whitespace collapsed. The
     * result is deliberately limited so the detail sheet stays readable.
     */
    public static String contextAround(String text, int wordStart, int wordEnd) {
        if (text == null || text.isEmpty() || wordStart < 0 || wordEnd > text.length()
                || wordStart >= wordEnd) {
            return "";
        }
        int start = wordStart;
        while (start > 0) {
            char previous = text.charAt(start - 1);
            if (previous == '\n' || previous == '.' || previous == '!'
                    || previous == '?') {
                break;
            }
            start--;
        }
        // Skip the whitespace or closing quote that sits between the previous
        // sentence and this one.
        while (start < wordStart && !Character.isLetterOrDigit(text.charAt(start))) {
            start++;
        }
        int end = wordEnd;
        while (end < text.length()) {
            char current = text.charAt(end);
            if (current == '\n') {
                end++;
                break;
            }
            if (current == '.' || current == '!' || current == '?') {
                end++;
                while (end < text.length()) {
                    char next = text.charAt(end);
                    if (next == '"' || next == '\'' || next == '\u201D' || next == '\u2019'
                            || next == ')' || next == ']' || next == '}') {
                        end++;
                    } else {
                        break;
                    }
                }
                break;
            }
            end++;
        }
        start = Math.max(0, start);
        end = Math.min(text.length(), end);

        // Keep the clicked word inside a reasonable window.
        boolean truncatedStart = false;
        boolean truncatedEnd = false;
        int maxLength = 420;
        if (end - start > maxLength) {
            int around = wordStart - start;
            int windowStart = Math.max(0, wordStart - Math.max(0, around - 120));
            int windowEnd = Math.min(text.length(), windowStart + maxLength);
            if (windowEnd < wordEnd) {
                windowEnd = Math.min(text.length(), wordEnd + 160);
                windowStart = Math.max(0, windowEnd - maxLength);
            }
            start = windowStart;
            end = windowEnd;
            if (start > 0) {
                int space = text.indexOf(' ', start);
                if (space > 0 && space < wordStart) {
                    start = space + 1;
                    truncatedStart = true;
                }
            }
            if (end < text.length()) {
                int space = text.lastIndexOf(' ', end);
                if (space > wordEnd) {
                    end = space;
                    truncatedEnd = true;
                }
            }
        }

        String sentence = text.substring(start, end).trim();
        sentence = sentence.replaceAll("\\s+", " ").trim();
        if (truncatedStart && !sentence.isEmpty()) {
            sentence = "\u2026" + sentence;
        }
        if (truncatedEnd && !sentence.isEmpty()) {
            sentence = sentence + "\u2026";
        }
        return sentence;
    }

    /** Turns ECDICT's ASCII-friendly phonetics into easier-to-read IPA. */
    public static String prettyPhonetic(String phonetic) {
        if (phonetic == null) {
            return "";
        }
        String value = phonetic.trim()
                .replace("ә", "ə")
                .replace("ɑ:", "ɑː")
                .replace("ɔ:", "ɔː")
                .replace("i:", "iː")
                .replace("u:", "uː")
                .replace(":", "ː")
                .replace("'", "ˈ")
                .replace(",", "ˌ")
                .replace("[", "")
                .replace("]", "")
                .replace("/", "");
        return value.replaceAll("\\s+", " ").trim();
    }

    public static String formatDate(long millis) {
        Calendar now = Calendar.getInstance();
        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(millis);
        String pattern = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
                ? "M月d日" : "yyyy年M月d日";
        return new SimpleDateFormat(pattern, Locale.CHINA).format(new Date(millis));
    }

    public static int readingMinutes(int wordCount) {
        return Math.max(1, (int) Math.ceil(wordCount / 180.0));
    }

    public static boolean isPossessive(String word) {
        return word.endsWith("'s") || word.endsWith("s'");
    }

    public static List<String> baseFormCandidates(String word) {
        List<String> candidates = new ArrayList<>();
        if (word == null || word.length() < 3) {
            return candidates;
        }
        if (word.endsWith("'s")) {
            candidates.add(word.substring(0, word.length() - 2));
        } else if (word.endsWith("s'")) {
            candidates.add(word.substring(0, word.length() - 1));
        }

        String stripped = word;
        if (stripped.endsWith("ies") && stripped.length() > 4) {
            candidates.add(stripped.substring(0, stripped.length() - 3) + "y");
        }
        if (stripped.endsWith("ves") && stripped.length() > 4) {
            candidates.add(stripped.substring(0, stripped.length() - 3) + "f");
            candidates.add(stripped.substring(0, stripped.length() - 3) + "fe");
        }
        if (stripped.endsWith("es") && stripped.length() > 4) {
            candidates.add(stripped.substring(0, stripped.length() - 2));
        }
        if (stripped.endsWith("s") && !stripped.endsWith("ss") && stripped.length() > 3) {
            candidates.add(stripped.substring(0, stripped.length() - 1));
        }
        if (stripped.endsWith("ied") && stripped.length() > 4) {
            candidates.add(stripped.substring(0, stripped.length() - 3) + "y");
        }
        if (stripped.endsWith("ed") && stripped.length() > 4) {
            String base = stripped.substring(0, stripped.length() - 2);
            candidates.add(base);
            candidates.add(base + "e");
            addUndoubled(candidates, base);
        }
        if (stripped.endsWith("ing") && stripped.length() > 5) {
            String base = stripped.substring(0, stripped.length() - 3);
            candidates.add(base);
            candidates.add(base + "e");
            addUndoubled(candidates, base);
        }
        if (stripped.endsWith("est") && stripped.length() > 5) {
            String base = stripped.substring(0, stripped.length() - 3);
            candidates.add(base);
            candidates.add(base + "e");
            addUndoubled(candidates, base);
        }
        if (stripped.endsWith("er") && stripped.length() > 4) {
            String base = stripped.substring(0, stripped.length() - 2);
            candidates.add(base);
            candidates.add(base + "e");
            addUndoubled(candidates, base);
        }
        if (stripped.endsWith("ly") && stripped.length() > 4) {
            candidates.add(stripped.substring(0, stripped.length() - 2));
        }
        if (word.contains("-")) {
            candidates.add(word.replace("-", ""));
        }

        List<String> unique = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate != null && candidate.length() > 1
                    && !candidate.equals(word) && !unique.contains(candidate)) {
                unique.add(candidate);
            }
        }
        return unique;
    }

    private static void addUndoubled(List<String> candidates, String base) {
        int length = base.length();
        if (length >= 3
                && base.charAt(length - 1) == base.charAt(length - 2)
                && !"aeiou".contains(String.valueOf(base.charAt(length - 1)).toLowerCase(Locale.ROOT))) {
            candidates.add(base.substring(0, length - 1));
        }
    }

    /**
     * Splits "n. 苹果\nv. 吃" into POS and meaning pairs and returns a styled
     * string. Domain labels such as [医] are preserved but de-emphasised.
     */
    public static List<String[]> splitTranslations(String translation) {
        List<String[]> lines = new ArrayList<>();
        if (translation == null || translation.trim().isEmpty()) {
            return lines;
        }
        String[] rawLines = translation.split("\\n");
        for (String raw : rawLines) {
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            Matcher matcher = POS_PATTERN.matcher(line);
            if (matcher.matches()) {
                lines.add(new String[]{matcher.group(1), matcher.group(2)});
            } else {
                lines.add(new String[]{"", line});
            }
        }
        return lines;
    }

    private static boolean isPartOfSpeech(String token) {
        String lower = token.toLowerCase(Locale.ROOT);
        switch (lower) {
            case "n":
            case "v":
            case "s":
            case "a":
            case "r":
            case "vt":
            case "vi":
            case "adj":
            case "adv":
            case "prep":
            case "pron":
            case "conj":
                return true;
            default:
                return false;
        }
    }

    public static List<String[]> splitDefinitions(String definition) {
        List<String[]> lines = new ArrayList<>();
        if (definition == null || definition.trim().isEmpty()) {
            return lines;
        }
        for (String raw : definition.split("\\n")) {
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            Matcher matcher = Pattern.compile("^([A-Za-z]{1,4})\\.?\\s+(.*)$").matcher(line);
            if (matcher.matches() && isPartOfSpeech(matcher.group(1))) {
                lines.add(new String[]{matcher.group(1), matcher.group(2)});
            } else {
                lines.add(new String[]{"", line});
            }
        }
        return lines;
    }
}
