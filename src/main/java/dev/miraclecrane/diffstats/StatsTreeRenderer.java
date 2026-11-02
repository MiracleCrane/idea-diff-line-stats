package dev.miraclecrane.diffstats;

import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNodeRenderer;
import com.intellij.openapi.vcs.changes.ui.ChangesTree;
import java.awt.Component;
import java.awt.Container;
import java.util.IdentityHashMap;
import java.util.Map;
import javax.swing.JTree;
import javax.swing.tree.TreeCellRenderer;

/**
 * 保留原生渲染器及复选框，仅在原文字后追加统计。 缓存按节点身份隔离，重建模型后释放，不改变树模型、双击或选择行为。
 *
 * @author MiracleCrane
 * @version 2026-11-02
 */
final class StatsTreeRenderer implements TreeCellRenderer {
  private final TreeCellRenderer delegate;
  private final Map<ChangesBrowserNode<?>, StatsEntry> entries = new IdentityHashMap<>();
  private Object modelRoot;

  /**
   * 包装现有渲染器。
   *
   * @param delegate IDEA 当前使用的渲染器
   */
  StatsTreeRenderer(TreeCellRenderer delegate) {
    this.delegate = delegate;
  }

  /** {@inheritDoc} */
  @Override
  public Component getTreeCellRendererComponent(
      JTree tree,
      Object value,
      boolean selected,
      boolean expanded,
      boolean leaf,
      int row,
      boolean focus) {
    Component component =
        delegate.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, focus);
    Object root = tree.getModel().getRoot();
    if (modelRoot != root) {
      entries.clear();
      modelRoot = root;
    }
    if (tree instanceof ChangesTree changesTree
        && value instanceof ChangesBrowserNode<?> node
        && node.getUserObject() instanceof Change change) {
      var revision =
          change.getAfterRevision() != null
              ? change.getAfterRevision()
              : change.getBeforeRevision();
      var renderer = textRenderer(component);
      if (renderer != null && revision != null && !revision.getFile().isDirectory()) {
        entries
            .computeIfAbsent(node, key -> new StatsEntry(changesTree.getProject(), change))
            .decorate(tree, node, renderer);
      }
    }
    return component;
  }

  /**
   * 查找原生复合渲染器中的文字组件。
   *
   * @param component 已经按原生方式渲染的组件
   * @return 文字渲染器；不支持的组件返回空
   */
  static ChangesBrowserNodeRenderer textRenderer(Component component) {
    if (component instanceof ChangesBrowserNodeRenderer renderer) {
      return renderer;
    }
    if (component instanceof Container container) {
      for (Component child : container.getComponents()) {
        var renderer = textRenderer(child);
        if (renderer != null) {
          return renderer;
        }
      }
    }
    return null;
  }
}
