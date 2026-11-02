package dev.miraclecrane.diffstats;

/**
 * 一次左右版本比较的行数结果。 非文本和未成功计算的结果保留原因，避免把失败误报为零改动。
 *
 * @author MiracleCrane
 * @version 2026-11-02
 * @param added 新增行数
 * @param deleted 删除行数
 * @param status 计算状态
 */
public record LineStats(int added, int deleted, Status status) {
  /**
   * 文件差异统计的可展示状态。 加载失败和超出计算上限分别展示，不能用于贡献量统计。
   *
   * @author MiracleCrane
   * @version 2026-11-02
   */
  public enum Status {
    READY,
    BINARY,
    TOO_LARGE,
    UNAVAILABLE
  }

  /**
   * 创建不包含有效数字的状态结果。
   *
   * @param status 不能完成数字统计的原因
   * @return 仅用于展示状态的结果
   */
  static LineStats unavailable(Status status) {
    return new LineStats(0, 0, status);
  }
}
