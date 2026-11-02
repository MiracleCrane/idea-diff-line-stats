package dev.miraclecrane.diffstats;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ui.ChangesTree;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Container;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.ContainerEvent;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JTree;
import javax.swing.Timer;

/**
 * 管理项目内的后台统计与文件修改后的列表重绘。 两个后台任务并行，树用弱引用登记，关闭项目时释放监听器与队列。
 *
 * @author MiracleCrane
 * @version 2026-11-02
 */
public final class StatsService implements Disposable {
  private final Project project;
  private final ExecutorService executor;
  private final Set<JTree> trees = Collections.newSetFromMap(new WeakHashMap<>());
  private final AtomicBoolean repaintQueued = new AtomicBoolean();
  private volatile boolean disposed;
  private final Set<EmptyProgressIndicator> active = ConcurrentHashMap.newKeySet();
  private final AWTEventListener treeListener =
      event -> {
        if (event instanceof ContainerEvent containerEvent
            && event.getID() == ContainerEvent.COMPONENT_ADDED) {
          Component child = containerEvent.getChild();
          // 等构造器完成再包装；不在 Swing 构造到一半时访问树的项目或渲染器。
          ApplicationManager.getApplication().invokeLater(() -> scan(child), ModalityState.any());
        }
      };

  /**
   * 建立随服务释放的文档和 VFS 监听器。
   *
   * @param project 所属项目
   */
  public StatsService(Project project) {
    this.project = project;
    // 文件统计属于项目生命周期，不继承一次 UI 渲染调用的短生命周期协程取消上下文。
    executor =
        Executors.newFixedThreadPool(
            2,
            task -> {
              Thread thread = new Thread(task, "Diff Line Stats");
              thread.setDaemon(true);
              return thread;
            });
    Toolkit.getDefaultToolkit().addAWTEventListener(treeListener, AWTEvent.CONTAINER_EVENT_MASK);
    ApplicationManager.getApplication()
        .invokeLater(
            () -> {
              for (Window window : Window.getWindows()) {
                scan(window);
              }
            },
            ModalityState.any());
    EditorFactory.getInstance()
        .getEventMulticaster()
        .addDocumentListener(
            new DocumentListener() {
              /** {@inheritDoc} */
              @Override
              public void documentChanged(DocumentEvent event) {
                requestRepaint();
              }
            },
            this);
    ApplicationManager.getApplication()
        .getMessageBus()
        .connect(this)
        .subscribe(
            VirtualFileManager.VFS_CHANGES,
            new BulkFileListener() {
              /** {@inheritDoc} */
              @Override
              public void after(List<? extends VFileEvent> events) {
                requestRepaint();
              }
            });
  }

  /**
   * 仅检查新加入界面的组件子树，不使用持续扫描计时器。
   *
   * @param component 新组件或启动时已有窗口
   */
  private void scan(Component component) {
    if (disposed) {
      return;
    }
    if (component instanceof ChangesTree tree && tree.getProject() == project) {
      if (trees.add(tree)) {
        tree.addPropertyChangeListener("cellRenderer", event -> wrap(tree));
      }
      wrap(tree);
      return;
    }
    if (component instanceof Container container) {
      for (Component child : container.getComponents()) {
        scan(child);
      }
    }
  }

  /**
   * 包装尚未增强的原生渲染器，允许 IDEA 切换主题后重新设置渲染器。
   *
   * @param tree 当前项目的原生变更树
   */
  private void wrap(ChangesTree tree) {
    if (!disposed
        && tree.getCellRenderer() != null
        && !(tree.getCellRenderer() instanceof StatsTreeRenderer)) {
      tree.setCellRenderer(new StatsTreeRenderer(tree.getCellRenderer()));
    }
  }

  /**
   * 排队执行统计，项目已关闭时不再接收工作。
   *
   * @param task 仅包含文件读取及差异计算的后台任务
   */
  void execute(Runnable task) {
    if (!disposed && !project.isDisposed()) {
      executor.execute(
          () -> {
            EmptyProgressIndicator indicator = new EmptyProgressIndicator();
            active.add(indicator);
            try {
              if (!disposed) {
                ProgressManager.getInstance().runProcess(task, indicator);
              }
            } finally {
              active.remove(indicator);
            }
          });
    }
  }

  /** 合并连续编辑事件，在 250 毫秒后触发一次重绘。 */
  private void requestRepaint() {
    if (disposed || !repaintQueued.compareAndSet(false, true)) {
      return;
    }
    ApplicationManager.getApplication()
        .invokeLater(
            () -> {
              if (disposed) {
                return;
              }
              Timer timer =
                  new Timer(
                      250,
                      event -> {
                        repaintQueued.set(false);
                        if (!disposed) {
                          trees.forEach(JTree::repaint);
                        }
                      });
              timer.setRepeats(false);
              timer.start();
            });
  }

  /** 关闭项目时中止待执行任务；监听器由平台按 Disposable 关系移除。 */
  @Override
  public void dispose() {
    disposed = true;
    Toolkit.getDefaultToolkit().removeAWTEventListener(treeListener);
    active.forEach(EmptyProgressIndicator::cancel);
    executor.shutdownNow();
  }
}
