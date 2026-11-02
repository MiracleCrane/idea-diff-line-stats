package dev.miraclecrane.diffstats;

/**
 * 覆盖影响用户所见数字的文本边界。 不需要外部测试框架，使用 IDEA 随附的差异算法运行。
 *
 * @author MiracleCrane
 * @version 2026-11-02
 */
public final class StatsCalculatorTest {
  /**
   * 执行计数回归。
   *
   * @param args 未使用
   */
  public static void main(String[] args) {
    check("empty", "", "", 0, 0);
    check("new file", "", "a\nb\n", 2, 0);
    check("deleted file", "a\nb\n", "", 0, 2);
    check("replace", "a\nb\nc\n", "a\nB\nC\nd\n", 3, 2);
    check("reverse", "a\nB\nC\nd\n", "a\nb\nc\n", 2, 3);
    check("no terminal newline", "", "a", 1, 0);
    check("add terminal newline", "a", "a\n", 1, 1);
    check("blank line", "a\n", "a\n\n", 1, 0);
    check("crlf", "a\r\nb\r\n", "a\nb\n", 0, 0);
    check("unicode", "你好\n旧\n", "你好\n新\n额外\n", 2, 1);
    check("whitespace", "a\n", " a\n", 1, 1);
    check("rename unchanged", "same\n", "same\n", 0, 0);
    if (StatsCalculator.compare("", "x\0y").status() != LineStats.Status.BINARY) {
      throw new AssertionError("binary detection");
    }
    if (StatsCalculator.compare("", "x".repeat(StatsCalculator.MAX_TEXT_CHARS + 1)).status()
        != LineStats.Status.TOO_LARGE) {
      throw new AssertionError("large file limit");
    }
    System.out.println("PASS: 14 text-boundary checks");
  }

  /**
   * 比较预期计数。
   *
   * @param name 用例名
   * @param before 左侧文本
   * @param after 右侧文本
   * @param added 预期新增
   * @param deleted 预期删除
   */
  private static void check(String name, String before, String after, int added, int deleted) {
    LineStats actual = StatsCalculator.compare(before, after);
    if (!actual.equals(new LineStats(added, deleted, LineStats.Status.READY))) {
      throw new AssertionError(name + ": " + actual);
    }
  }
}
