package dev.miraclecrane.diffstats;

import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNodeRenderer;
import com.intellij.openapi.vcs.changes.ui.ChangesTree;
import java.awt.Component;
import java.awt.Container;
import javax.swing.JTree;
import javax.swing.tree.TreeCellRenderer;

/**
 * 保留原生渲染器及复选框，在文件标签后显示已经预加载的统计。 渲染过程不再负责建立缓存或启动后台读取，因此滚动不会触发逐行加载。
 *
 * @author MiracleCrane
 * @version 2026-11-02
 */
final class StatsTreeRenderer implements TreeCellRenderer {
  private final TreeCellRenderer delegate;
  private final StatsTreeState state;

  /**
   * 包装现有渲染器。
   *
   * @param delegate IDEA 当前使用的渲染器
   * @param state 当前树独立于渲染的统计缓存
   */
  StatsTreeRenderer(TreeCellRenderer delegate, StatsTreeState state) {
    this.delegate = delegate;
    this.state = state;
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
    if (tree instanceof ChangesTree
        && value instanceof ChangesBrowserNode<?> node
        && node.getUserObject() instanceof Change change) {
      var revision =
          change.getAfterRevision() != null
              ? change.getAfterRevision()
              : change.getBeforeRevision();
      var renderer = textRenderer(component);
      if (renderer != null && revision != null && !revision.getFile().isDirectory()) {
        state.decorate(node, renderer);
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
