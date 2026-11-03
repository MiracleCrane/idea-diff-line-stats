package dev.miraclecrane.diffstats;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNodeRenderer;
import com.intellij.openapi.vcs.changes.ui.ChangesTree;
import com.intellij.ui.SimpleTextAttributes;
import java.beans.PropertyChangeListener;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;

/**
 * 按完整树模型预加载所有文件的统计，不依赖展开、滚动或渲染。 所有缓存变更在 EDT 上完成，内容读取交给项目后台线程；弱引用避免保留已关闭的文件树。
 *
 * @author MiracleCrane
 * @version 2026-11-03
 */
final class StatsTreeState implements TreeModelListener {
  private final Project project;
  private final WeakReference<ChangesTree> treeReference;
  private final Map<ChangesBrowserNode<?>, StatsEntry> entries = new IdentityHashMap<>();
  private final PropertyChangeListener modelListener = event -> attachModel();
  private TreeModel model;
  private boolean refreshQueued;
  private boolean updatingPresentation;
  private volatile boolean disposed;

  /**
   * 监听模型更换和节点变化，并立即安排整棵树预加载。
   *
   * @param project 所属项目
   * @param tree 原生差异文件树
   */
  StatsTreeState(Project project, ChangesTree tree) {
    this.project = project;
    treeReference = new WeakReference<>(tree);
    tree.addPropertyChangeListener("model", modelListener);
    attachModel();
  }

  /** 绑定当前模型，刷新、换分组或切换比较后不会继续监听旧模型。 */
  private void attachModel() {
    ChangesTree tree = treeReference.get();
    if (disposed || tree == null) {
      return;
    }
    if (model != null) {
      model.removeTreeModelListener(this);
    }
    model = tree.getModel();
    if (model != null) {
      model.addTreeModelListener(this);
    }
    requestRefresh();
  }

  /** 合并本轮模型或文档事件，在原生模型修改完成后遍历全部节点。 */
  void requestRefresh() {
    if (disposed || refreshQueued) {
      return;
    }
    refreshQueued = true;
    ApplicationManager.getApplication()
        .invokeLater(
            () -> {
              refreshQueued = false;
              preload();
            },
            ModalityState.any());
  }

  /** 遍历完整模型而非可见行，为隐藏文件创建缓存，并退役已经移除的节点。 */
  private void preload() {
    ChangesTree tree = treeReference.get();
    if (disposed || project.isDisposed() || tree == null) {
      return;
    }
    Set<ChangesBrowserNode<?>> present = Collections.newSetFromMap(new IdentityHashMap<>());
    if (model != null && model.getRoot() instanceof ChangesBrowserNode<?> root) {
      for (ChangesBrowserNode<?> node : root.traverse()) {
        if (!(node.getUserObject() instanceof Change change)) {
          continue;
        }
        var revision =
            change.getAfterRevision() != null
                ? change.getAfterRevision()
                : change.getBeforeRevision();
        if (revision == null || revision.getFile().isDirectory()) {
          continue;
        }
        present.add(node);
        StatsEntry entry = entries.get(node);
        if (entry == null || !entry.matches(change)) {
          if (entry != null) {
            entry.retire();
          }
          entry = new StatsEntry(project, change);
          entries.put(node, entry);
        }
        entry.refresh(() -> publish(node));
      }
    }
    entries
        .entrySet()
        .removeIf(
            entry -> {
              if (present.contains(entry.getKey())) {
                return false;
              }
              entry.getValue().retire();
              return true;
            });
    tree.repaint();
  }

  /**
   * 通知原生树重新计算文字宽度，不将自身重绘事件误认为需要再次预加载。
   *
   * @param node 已完成或重新排队的文件节点
   */
  private void publish(ChangesBrowserNode<?> node) {
    ChangesTree tree = treeReference.get();
    if (disposed || tree == null || model == null || model.getRoot() != node.getRoot()) {
      return;
    }
    updatingPresentation = true;
    try {
      if (model instanceof DefaultTreeModel defaultModel) {
        defaultModel.nodeChanged(node);
      }
    } finally {
      updatingPresentation = false;
    }
    tree.repaint();
  }

  /**
   * 只展示预加载结果；新模型尚未遍历时显示短暂的待计算标记。
   *
   * @param node 原生文件节点
   * @param renderer 原生文字渲染器
   */
  void decorate(ChangesBrowserNode<?> node, ChangesBrowserNodeRenderer renderer) {
    StatsEntry entry = entries.get(node);
    if (entry == null) {
      renderer.append("  …", SimpleTextAttributes.GRAYED_ATTRIBUTES, false);
    } else {
      entry.decorate(renderer);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void treeNodesChanged(TreeModelEvent event) {
    if (!updatingPresentation) {
      requestRefresh();
    }
  }

  /** {@inheritDoc} */
  @Override
  public void treeNodesInserted(TreeModelEvent event) {
    requestRefresh();
  }

  /** {@inheritDoc} */
  @Override
  public void treeNodesRemoved(TreeModelEvent event) {
    requestRefresh();
  }

  /** {@inheritDoc} */
  @Override
  public void treeStructureChanged(TreeModelEvent event) {
    requestRefresh();
  }

  /** 停止新的刷新，在 EDT 上解除模型监听并退役所有节点。 */
  void dispose() {
    disposed = true;
    ApplicationManager.getApplication()
        .invokeLater(
            () -> {
              ChangesTree tree = treeReference.get();
              if (tree != null) {
                tree.removePropertyChangeListener("model", modelListener);
              }
              if (model != null) {
                model.removeTreeModelListener(this);
              }
              entries.values().forEach(StatsEntry::retire);
              entries.clear();
            },
            ModalityState.any());
  }
}
