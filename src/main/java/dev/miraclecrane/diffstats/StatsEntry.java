package dev.miraclecrane.diffstats;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.CurrentContentRevision;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNodeRenderer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;

/**
 * 保存单个变更的后台统计，并在文件标签后展示结果。 计算由整棵树的预加载流程触发；渲染只读缓存，节点失效后取消过期任务的发布。
 *
 * @author MiracleCrane
 * @version 2026-11-02
 */
public final class StatsEntry {
  private static final Logger LOG = Logger.getInstance(StatsEntry.class);
  private static final SimpleTextAttributes ADDED =
      new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, new JBColor(0x247A38, 0x73C991));
  private static final SimpleTextAttributes DELETED =
      new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, new JBColor(0xBF3131, 0xF38B8B));
  private final Project project;
  private final Change change;
  private LineStats result;
  private String resultStamp;
  private boolean pending;
  private volatile boolean retired;

  /**
   * 保存当前节点对应的比较对象。
   *
   * @param project 所属项目
   * @param change 当前比较的变更
   */
  StatsEntry(Project project, Change change) {
    this.project = project;
    this.change = change;
  }

  /**
   * 检查缓存并为新内容启动后台任务，不要求节点已出现在视口中。
   *
   * @param updated 结果发布后在 EDT 上通知所属树
   */
  void refresh(Runnable updated) {
    if (retired || project.isDisposed()) {
      return;
    }
    StatsService service = project.getService(StatsService.class);
    String stamp = currentStamp();
    if (!stamp.equals(resultStamp)) {
      result = null;
      if (!pending) {
        pending = true;
        service.execute(() -> compute(stamp, updated));
      }
    }
  }

  /**
   * 在原生文件标签后追加缓存的统计，不启动读取或计算。
   *
   * @param renderer 已完成原生绘制的文字组件
   */
  void decorate(ChangesBrowserNodeRenderer renderer) {
    if (result == null) {
      renderer.append("  …", SimpleTextAttributes.GRAYED_ATTRIBUTES, false);
    } else if (result.status() == LineStats.Status.READY) {
      renderer.append("  +" + result.added(), ADDED, false);
      renderer.append("  −" + result.deleted(), DELETED, false);
    } else {
      String label =
          switch (result.status()) {
            case BINARY -> "二进制";
            case TOO_LARGE -> "超大文件";
            default -> "统计不可用";
          };
      renderer.append("  [" + label + "]", SimpleTextAttributes.GRAYED_ATTRIBUTES, false);
    }
  }

  /**
   * 判断原生节点是否仍引用相同比较对象。
   *
   * @param candidate 节点当前承载的变更
   * @return 是否可以继续使用本缓存
   */
  boolean matches(Change candidate) {
    return change == candidate;
  }

  /** 标记已经移除或替换的节点，排队任务会跳过，正在计算的结果不再发布。 */
  void retire() {
    retired = true;
  }

  /**
   * 在后台读取版本，回到 EDT 后核验节点及版本仍有效。
   *
   * @param stamp 任务排队时的本地内容版本
   * @param updated 结果发布后的界面通知
   */
  private void compute(String stamp, Runnable updated) {
    if (retired || project.isDisposed()) {
      return;
    }
    LineStats computed;
    try {
      computed = StatsCalculator.calculate(change);
    } catch (ProcessCanceledException exception) {
      computed = LineStats.unavailable(LineStats.Status.UNAVAILABLE);
    } catch (Exception exception) {
      LOG.debug("Cannot calculate diff line counts", exception);
      computed = LineStats.unavailable(LineStats.Status.UNAVAILABLE);
    }
    LineStats completed = computed;
    ApplicationManager.getApplication()
        .invokeLater(
            () -> {
              pending = false;
              if (retired || project.isDisposed()) {
                return;
              }
              // 输入变化则直接重新排队，无需等待此文件再次滚入可见区域。
              if (stamp.equals(currentStamp())) {
                resultStamp = stamp;
                result = completed;
              } else {
                refresh(updated);
              }
              updated.run();
            },
            ModalityState.any());
  }

  /**
   * 合并左右两侧本地内容的修改标记。
   *
   * @return 当前节点内容版本，不跨不同变更对象共用缓存
   */
  private String currentStamp() {
    return revisionStamp(change.getBeforeRevision())
        + ":"
        + revisionStamp(change.getAfterRevision());
  }

  /**
   * 获取本地文档及磁盘版本，历史版本随节点生命周期保持不变。
   *
   * @param revision 要检查的版本
   * @return 本地修改标记或历史版本固定标记
   */
  private static String revisionStamp(ContentRevision revision) {
    if (!(revision instanceof CurrentContentRevision)) {
      return "fixed";
    }
    VirtualFile file = revision.getFile().getVirtualFile();
    if (file == null || !file.isValid()) {
      return "missing";
    }
    var documents = FileDocumentManager.getInstance();
    var document = documents.getCachedDocument(file);
    // 已保存文档可能被弱缓存回收并重新加载；缓存有无不是内容变化。
    // 只给未保存内容加文档标记，否则后台读取本身会使缓存反复失效。
    if (document != null && documents.isDocumentUnsaved(document)) {
      return file.getModificationStamp() + "/unsaved/" + document.getModificationStamp();
    }
    return Long.toString(file.getModificationStamp());
  }
}
