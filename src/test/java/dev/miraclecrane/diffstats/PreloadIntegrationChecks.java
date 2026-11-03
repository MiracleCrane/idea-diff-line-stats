package dev.miraclecrane.diffstats;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.LocalFilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.SimpleContentRevision;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode;
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingSupport;
import com.intellij.openapi.vcs.changes.ui.SimpleChangesBrowser;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;

/**
 * 在没有统计文字渲染的原生树中验证全量预加载。 用可计数的版本内容作为独立观测，确保折叠、视口与模型更换都不会阻止后台读取。
 *
 * @author MiracleCrane
 * @version 2026-11-03
 */
final class PreloadIntegrationChecks {
  private static final int FILE_COUNT = 120;

  /** 禁止实例化测试工具。 */
  private PreloadIntegrationChecks() {}

  /**
   * 验证初次全量加载、切换模型和在同一模型中插入新文件。
   *
   * @param project 已打开的测试项目
   * @param root 仅属于测试的目录
   * @throws Exception 超时或平台调用失败时抛出
   */
  static void run(Project project, Path root) throws Exception {
    AtomicInteger firstReads = new AtomicInteger();
    List<Change> first = changes(root, "first", FILE_COUNT, firstReads);
    AtomicReference<SimpleChangesBrowser> reference = new AtomicReference<>();
    ApplicationManager.getApplication()
        .invokeAndWait(
            () -> {
              var browser = new SimpleChangesBrowser(project, List.of());
              var tree = browser.getViewer();
              // 此渲染器没有原生统计文字组件；即使树尝试测量隐藏行，也不能触发旧版的逐行加载。
              tree.setCellRenderer(new DefaultTreeCellRenderer());
              tree.getGroupingSupport()
                  .setGroupingKeys(List.of(ChangesGroupingSupport.DIRECTORY_GROUPING));
              browser.setChangesToDisplay(first);
              tree.setSize(240, 60);
              for (int row = tree.getRowCount() - 1; row >= 0; row--) {
                tree.collapseRow(row);
              }
              reference.set(browser);
            });
    awaitReads(firstReads, FILE_COUNT * 2);
    System.out.println("PASS: preload 120 collapsed/off-screen files without statistics rendering");

    AtomicInteger replacementReads = new AtomicInteger();
    List<Change> replacement = changes(root, "replacement", FILE_COUNT, replacementReads);
    ApplicationManager.getApplication()
        .invokeAndWait(() -> reference.get().setChangesToDisplay(replacement));
    awaitReads(replacementReads, FILE_COUNT * 2);

    AtomicInteger insertedReads = new AtomicInteger();
    Change inserted = changes(root, "inserted", 1, insertedReads).getFirst();
    ApplicationManager.getApplication()
        .invokeAndWait(
            () -> {
              var tree = reference.get().getViewer();
              var model = (DefaultTreeModel) tree.getModel();
              var parent = tree.getRoot();
              model.insertNodeInto(
                  ChangesBrowserNode.createChange(project, inserted),
                  parent,
                  parent.getChildCount());
            });
    awaitReads(insertedReads, 2);
    if (firstReads.get() != FILE_COUNT * 2 || replacementReads.get() != FILE_COUNT * 2) {
      throw new AssertionError("Completed revisions were unnecessarily loaded again");
    }
    System.out.println(
        "PASS: model replacement and incremental insertion preload without scrolling");
  }

  /**
   * 构造可计数的比较版本，避免借助渲染缓存反向证明自身实现。
   *
   * @param root 测试根目录
   * @param group 当前比较批次
   * @param count 文件数量
   * @param reads 后台实际读取次数
   * @return 文件变更列表
   */
  private static List<Change> changes(Path root, String group, int count, AtomicInteger reads) {
    List<Change> changes = new ArrayList<>();
    for (int index = 0; index < count; index++) {
      var path =
          new LocalFilePath(root.resolve("synthetic/" + group + "/File" + index + ".txt"), false);
      changes.add(
          new Change(
              revision("old\n", path, "before", reads),
              revision("new\nextra\n", path, "after", reads)));
    }
    return changes;
  }

  /**
   * 记录内容读取，并拒绝在 UI 线程执行文件读取。
   *
   * @param text 固定版本内容
   * @param path 测试文件路径
   * @param version 版本标识
   * @param reads 读取计数器
   * @return 可计数的原生内容版本
   */
  private static SimpleContentRevision revision(
      String text, LocalFilePath path, String version, AtomicInteger reads) {
    return new SimpleContentRevision(text, path, version) {
      /** {@inheritDoc} */
      @Override
      public String getContent() {
        if (SwingUtilities.isEventDispatchThread()) {
          throw new AssertionError("Version content was read on the EDT");
        }
        reads.incrementAndGet();
        return super.getContent();
      }
    };
  }

  /**
   * 不调用任何文件行渲染，等待内容读取完成。
   *
   * @param reads 独立内容读取计数
   * @param expected 所有左右版本的期望读取次数
   * @throws InterruptedException 测试进程被中断时抛出
   */
  private static void awaitReads(AtomicInteger reads, int expected) throws InterruptedException {
    long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(20);
    while (reads.get() < expected && System.nanoTime() < deadline) {
      Thread.sleep(50);
    }
    if (reads.get() != expected) {
      throw new AssertionError(
          "Expected " + expected + " background revision reads, got " + reads.get());
    }
  }
}
