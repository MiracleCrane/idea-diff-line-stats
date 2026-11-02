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
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNodeRenderer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeModel;

/**
 * 在原生变更节点文字后绘制异步统计。 每个节点对应唯一的左右版本；本地文档变化时失效，旧任务结果不覆盖新版本。
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
   * 渲染已缓存的统计，内容读取与计算均在后台进行。
   *
   * @param tree 当前文件树
   * @param node 原生节点
   * @param renderer 原生文字渲染器
   */
  public void decorate(
      JTree tree, ChangesBrowserNode<?> node, ChangesBrowserNodeRenderer renderer) {
    if (project.isDisposed()) {
      return;
    }
    StatsService service = project.getService(StatsService.class);
    String stamp = currentStamp();
    if (!stamp.equals(resultStamp)) {
      result = null;
      if (!pending) {
        pending = true;
        service.execute(() -> compute(tree, node, stamp));
      }
    }
    if (result == null) {
      renderer.append("  …", SimpleTextAttributes.GRAYED_ATTRIBUTES);
    } else if (result.status() == LineStats.Status.READY) {
      renderer.append("  +" + result.added(), ADDED);
      renderer.append("  −" + result.deleted(), DELETED);
    } else {
      String label =
          switch (result.status()) {
            case BINARY -> "二进制";
            case TOO_LARGE -> "超大文件";
            default -> "统计不可用";
          };
      renderer.append("  [" + label + "]", SimpleTextAttributes.GRAYED_ATTRIBUTES);
    }
  }

  /**
   * 在后台读取版本，回到 EDT 后核验节点及版本仍有效。
   *
   * @param tree 触发计算的文件树
   * @param node 触发计算的原生节点
   * @param stamp 任务排队时的本地内容版本
   */
  private void compute(JTree tree, ChangesBrowserNode<?> node, String stamp) {
    if (project.isDisposed()) {
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
              if (project.isDisposed() || tree.getModel().getRoot() != node.getRoot()) {
                return;
              }
              // 输入变化则舍弃旧结果，下次渲染启动新任务，避免快速连续编辑显示旧数字。
              if (stamp.equals(currentStamp())) {
                resultStamp = stamp;
                result = completed;
              }
              if (tree.getModel() instanceof DefaultTreeModel model) {
                model.nodeChanged(node);
              }
              tree.repaint();
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
