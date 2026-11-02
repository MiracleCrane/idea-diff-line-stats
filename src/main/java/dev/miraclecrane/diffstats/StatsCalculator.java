package dev.miraclecrane.diffstats;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.BinaryContentRevision;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.util.diff.Diff;
import com.intellij.util.diff.FilesTooBigForDiffException;
import java.util.ArrayList;
import java.util.List;

/**
 * 计算差异列表中实际左右版本的新增、删除行数。 复用平台差异算法；不从当前分支名推断基线，不执行 Git 写入。
 *
 * @author MiracleCrane
 * @version 2026-11-02
 */
public final class StatsCalculator {
  // 每侧最多 2 Mi 字符，限制后台分词和差异算法的内存占用。
  static final int MAX_TEXT_CHARS = 2 * 1024 * 1024;
  static final int MAX_LINES = 100_000;

  /** 禁止实例化无状态计算器。 */
  private StatsCalculator() {}

  /**
   * 读取一个变更的左右版本并计算差异。
   *
   * @param change 当前列表实际使用的变更
   * @return 行数或可解释的不可用状态
   * @throws VcsException 版本内容读取失败时抛出
   */
  static LineStats calculate(Change change) throws VcsException {
    ContentRevision before = change.getBeforeRevision();
    ContentRevision after = change.getAfterRevision();
    if (isBinary(before) || isBinary(after)) {
      return LineStats.unavailable(LineStats.Status.BINARY);
    }
    String oldText = before == null ? "" : before.getContent();
    String newText = after == null ? "" : after.getContent();
    // 缺少一侧版本表示新增/删除；版本存在但返回 null 表示读取不可用。
    if (oldText == null || newText == null) {
      return LineStats.unavailable(LineStats.Status.UNAVAILABLE);
    }
    return compare(oldText, newText);
  }

  /**
   * 排除二进制内容，避免把字节数据误当文本行。
   *
   * @param revision 一侧版本；新增或删除时允许为空
   * @return 是否为平台识别的二进制文件
   */
  private static boolean isBinary(ContentRevision revision) {
    return revision != null
        && (revision instanceof BinaryContentRevision
            || revision.getFile().getFileType().isBinary());
  }

  /**
   * 比较两个文本，替换一行按一删一增计数。
   *
   * @param before 左侧文本
   * @param after 右侧文本
   * @return 与比较方向一致的结果
   */
  public static LineStats compare(String before, String after) {
    if (before.length() > MAX_TEXT_CHARS || after.length() > MAX_TEXT_CHARS) {
      return LineStats.unavailable(LineStats.Status.TOO_LARGE);
    }
    if (before.indexOf('\0') >= 0 || after.indexOf('\0') >= 0) {
      return LineStats.unavailable(LineStats.Status.BINARY);
    }
    String[] oldLines = lines(before);
    String[] newLines = lines(after);
    if (oldLines.length > MAX_LINES || newLines.length > MAX_LINES) {
      return LineStats.unavailable(LineStats.Status.TOO_LARGE);
    }
    try {
      int added = 0;
      int deleted = 0;
      for (Diff.Change block = Diff.buildChanges(oldLines, newLines);
          block != null;
          block = block.link) {
        added += block.inserted;
        deleted += block.deleted;
      }
      return new LineStats(added, deleted, LineStats.Status.READY);
    } catch (FilesTooBigForDiffException exception) {
      return LineStats.unavailable(LineStats.Status.TOO_LARGE);
    }
  }

  /**
   * 按真实文本行拆分，忽略 CRLF/LF 的平台差异。 每行保留终止符，因此末尾是否换行的变化仍记为一删一增；空文件为零行。
   *
   * @param text 输入文本
   * @return 不含虚构末尾空行的行数组
   */
  private static String[] lines(String text) {
    String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
    List<String> result = new ArrayList<>();
    int start = 0;
    for (int end = 0; end < normalized.length(); end++) {
      if (normalized.charAt(end) == '\n') {
        result.add(normalized.substring(start, end + 1));
        start = end + 1;
      }
    }
    if (start < normalized.length()) {
      result.add(normalized.substring(start));
    }
    return result.toArray(String[]::new);
  }
}
