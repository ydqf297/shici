package com.shici.reader;

/**
 * A small, hand-written reading used to seed the shelf on first launch and to
 * offer a safe "try before importing" option.
 */
public final class SampleContent {

    public static final String TITLE = "The Quiet Power of Small Steps";
    public static final String BODY =
            "The Quiet Power of Small Steps\n\n"
            + "We often imagine that progress arrives in a single, dramatic moment. "
            + "We wait for motivation to strike, for the perfect plan to appear, "
            + "for the day when everything finally clicks. Yet the most durable changes "
            + "rarely announce themselves. They grow quietly, in the ordinary hours we "
            + "tend to overlook.\n\n"
            + "A person who wants to read more does not begin by finishing a library. "
            + "She begins with ten minutes before bed. A runner does not set out to "
            + "conquer a marathon on the first morning; he simply laces his shoes and "
            + "reaches the end of the street. These choices look almost insignificant, "
            + "but they carry a hidden advantage: they are small enough to repeat.\n\n"
            + "Repetition is where the real transformation lives. Each repetition "
            + "lowers the cost of the next one. What once required willpower becomes a "
            + "habit, and what is habitual no longer feels like a burden. Over time, "
            + "the person is not merely doing something different; she is becoming "
            + "someone different.\n\n"
            + "So perhaps the question is not whether we can make a bold leap, but "
            + "whether we can begin with something modest and return to it tomorrow. "
            + "A page, a minute, a single honest attempt: none of these seems like "
            + "much. Collect enough of them, however, and they quietly reshape a life.";

    public static final String ANALYSIS_TITLE = "The Development of Technology";

    /**
     * A compact example of the Markdown-ish import format. It deliberately
     * includes bold text in the passage and full-width punctuation in the
     * explanations so the sample also exercises the parser.
     */
    public static final String ANALYSIS =
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
            + "    *   `ongoing` 是形容词，常用于描述正在进行、尚未结束的事情（如：ongoing project 持续进行的项目）。\n\n"
            + "#### 2. A skilled developer usually plays a key role in this developmental stage.\n"
            + "*   **逐词/短语拆解**：\n"
            + "    *   A skilled developer (一位熟练的开发者)\n"
            + "    *   usually (通常)\n"
            + "    *   plays a key role (起关键作用)\n"
            + "    *   in this developmental stage (在这个发展阶段).\n"
            + "*   **中文翻译**：一位熟练的开发者通常在这个发展阶段起着关键作用。\n"
            + "*   **语法与用法提示**：\n"
            + "    *   `developer` 指“开发者”（如软件开发者），由 `develop` + `-er` 后缀构成。\n"
            + "    *   `developmental` 是形容词，意为“发展的，发育的”，修饰 `stage`（阶段）。\n"
            + "    *   `play a key role in...` 是四级高频短语，意为“在……中起关键作用”。\n";

    private SampleContent() {
    }
}
