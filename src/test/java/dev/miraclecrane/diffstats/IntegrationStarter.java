package dev.miraclecrane.diffstats;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationStarter;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNodeRenderer;
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingSupport;
import com.intellij.openapi.vcs.changes.ui.ChangesTree;
import com.intellij.openapi.vcs.changes.ui.SimpleChangesBrowser;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.util.ui.ThreeStateCheckBox;
import git4idea.changes.GitChangeUtils;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;

/**
 * 在独立 IDEA 进程中验证真实插件注册、Git 版本读取和原生列表渲染。 此启动器只进入测试包，不随交付插件发布。
 *
 * @author MiracleCrane
 * @version 2026-11-02
 */
public final class IntegrationStarter implements ApplicationStarter {
  /** {@inheritDoc} */
  @Override
  public boolean isHeadless() {
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public int getRequiredModality() {
    return NOT_IN_EDT;
  }

  /**
   * 执行原生集成验证并以进程退出码报告。
   *
   * @param args 启动参数
   */
  @Override
  public void main(List<String> args) {
    try {
      runChecks();
      System.out.println("PASS: IDEA plugin integration");
      System.exit(0);
    } catch (Throwable failure) {
      failure.printStackTrace();
      System.exit(1);
    }
  }

  /**
   * 测试原生 Git 比较、方向翻转、刷新和未保存编辑。
   *
   * @throws Exception 任一检查或平台调用失败时抛出
   */
  private void runChecks() throws Exception {
    Path rootPath = Path.of(System.getProperty("diffstats.test.repo"));
    Project project = ProjectManagerEx.getInstanceEx().loadAndOpenProject(rootPath.toString());
    var root = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(rootPath);
    new StatsStartupActivity().runActivity(project);
    ApplicationManager.getApplication()
        .invokeAndWait(
            () ->
                ProjectLevelVcsManager.getInstance(project)
                    .setDirectoryMappings(
                        List.of(new VcsDirectoryMapping(rootPath.toString(), "Git"))));
    Collection<Change> changes =
        GitChangeUtils.getDiffWithWorkingDir(project, root, "baseline", null, false);
    ChangesTree tree = createTree(project, changes);
    Map<String, String> labels = awaitLabels(tree);
    System.out.println("FORWARD " + labels);
    expect(labels, "Modified.txt", "+2  −1");
    expect(labels, "Added.txt", "+3  −0");
    expect(labels, "Deleted.txt", "+0  −2");
    expect(labels, "image.png", "二进制");
    expect(labels, "Renamed.txt", "+0  −0");
    expect(labels, "VeryLongFileNameForCheckingStatisticsInNarrowPanels.txt", "+2  −0");
    // 未打开编辑器的已保存文档是弱缓存；回收后统计仍必须稳定，不能永久停留在省略号。
    System.gc();
    Thread.sleep(200);
    expect(awaitLabels(tree), "Added.txt", "+3  −0");
    checkRendererLayout(tree);
    render(tree, Path.of(System.getProperty("diffstats.test.image")));
    var swapped =
        changes.stream().map(c -> new Change(c.getAfterRevision(), c.getBeforeRevision())).toList();
    Map<String, String> reversed = awaitLabels(createTree(project, swapped));
    System.out.println("REVERSE " + reversed);
    expect(reversed, "Modified.txt", "+1  −2");
    expect(reversed, "Added.txt", "+0  −3");
    expect(reversed, "Deleted.txt", "+2  −0");
    var modified = root.findChild("Modified.txt");
    ApplicationManager.getApplication()
        .invokeAndWait(
            () ->
                WriteAction.run(
                    () -> {
                      var document = FileDocumentManager.getInstance().getDocument(modified);
                      document.setText("same\nnew\nextra\nunsaved\n");
                    }));
    Map<String, String> updated = awaitLabels(tree, "Modified.txt", "+3  −1");
    System.out.println("UNSAVED " + updated);
    expect(updated, "Modified.txt", "+3  −1");
    Map<String, String> reopened =
        awaitLabels(
            createTree(
                project,
                GitChangeUtils.getDiffWithWorkingDir(project, root, "baseline", null, false)));
    expect(reopened, "Modified.txt", "+3  −1");
    System.out.println("PASS: rebuilt tree and repeated rendering");
    ApplicationManager.getApplication()
        .invokeAndWait(
            () -> {
              tree.setSelectionRow(1);
              if (tree.getSelectionCount() != 1) {
                throw new AssertionError("Tree selection broken");
              }
            });
    System.out.println("PASS: native selection retained");
    ApplicationManager.getApplication()
        .invokeAndWait(
            () ->
                tree.getGroupingSupport()
                    .setGroupingKeys(List.of(ChangesGroupingSupport.DIRECTORY_GROUPING)));
    expect(awaitLabels(tree), "VeryLongFileNameForCheckingStatisticsInNarrowPanels.txt", "+2  −0");
    checkRendererLayout(tree);
    System.out.println("PASS: directory grouping and trailing statistics");
    PreloadIntegrationChecks.run(project, rootPath);
  }

  /**
   * 使用分支比较同款 SimpleChangesBrowser 创建原生树。
   *
   * @param project 测试项目
   * @param changes 原生 Git API 返回的变更
   * @return 原生差异文件树
   * @throws Exception EDT 调用失败时抛出
   */
  private static ChangesTree createTree(Project project, Collection<Change> changes)
      throws Exception {
    AtomicReference<ChangesTree> result = new AtomicReference<>();
    ApplicationManager.getApplication()
        .invokeAndWait(
            () -> {
              var browser = new SimpleChangesBrowser(project, changes);
              result.set(browser.getViewer());
            });
    return result.get();
  }

  /**
   * 等待后台统计并检查实际渲染文本。
   *
   * @param tree 原生树
   * @return 文件名到完整展示文字的映射
   * @throws Exception 统计超时或渲染失败时抛出
   */
  private static Map<String, String> awaitLabels(ChangesTree tree) throws Exception {
    return awaitLabels(tree, null, null);
  }

  /**
   * 等待后台刷新后的具体值，覆盖文档事件合并期间仍保留上一结果的短暂状态。
   *
   * @param tree 原生树
   * @param expectedFile 需要更新的文件，初次加载时允许为空
   * @param expectedText 更新后的统计文字，初次加载时允许为空
   * @return 全部就绪且满足更新条件的文字
   * @throws Exception 刷新超时或平台调用失败时抛出
   */
  private static Map<String, String> awaitLabels(
      ChangesTree tree, String expectedFile, String expectedText) throws Exception {
    for (int attempt = 0; attempt < 200; attempt++) {
      Map<String, String> labels = new LinkedHashMap<>();
      ApplicationManager.getApplication()
          .invokeAndWait(
              () -> {
                var renderer = tree.getCellRenderer();
                if (!renderer.getClass().getName().endsWith("StatsTreeRenderer")) {
                  return;
                }
                for (var node : tree.getRoot().traverse()) {
                  if (node.getUserObject() instanceof Change change) {
                    Component rendered =
                        renderer.getTreeCellRendererComponent(
                            tree, node, false, true, true, 0, false);
                    var revision =
                        change.getAfterRevision() != null
                            ? change.getAfterRevision()
                            : change.getBeforeRevision();
                    var text = textRenderer(rendered);
                    labels.put(
                        revision.getFile().getName(), text.getCharSequence(false).toString());
                  }
                }
              });
      if (!labels.isEmpty()
          && labels.values().stream().noneMatch(s -> s.contains("…"))
          && (expectedFile == null
              || labels.getOrDefault(expectedFile, "").contains(expectedText))) {
        return labels;
      }
      if (attempt == 199) {
        System.out.println(
            "TIMEOUT LABELS " + labels + " renderer=" + tree.getCellRenderer().getClass());
      }
      Thread.sleep(100);
    }
    throw new AssertionError("Stats did not complete within 20 seconds");
  }

  /**
   * 从实际渲染组件读取文本。
   *
   * @param component 原生渲染组件
   * @return 原生文字组件
   */
  private static ChangesBrowserNodeRenderer textRenderer(Component component) {
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

  /**
   * 校验一个文件的实际文字包含预期统计。
   *
   * @param labels 渲染结果
   * @param name 文件名
   * @param expected 预期文字
   */
  private static void expect(Map<String, String> labels, String name, String expected) {
    if (!labels.getOrDefault(name, "").contains(expected)) {
      throw new AssertionError(name + " expected " + expected + ": " + labels);
    }
  }

  /**
   * 验证尾部统计、原生文本、选中状态、复选框和目录行的读屏状态。
   *
   * @param tree 已经加载统计的原生树
   */
  private static void checkRendererLayout(ChangesTree tree) {
    ApplicationManager.getApplication()
        .invokeAndWait(
            () -> {
              tree.setShowCheckboxes(true);
              var cellRenderer = tree.getCellRenderer();
              for (var node : tree.getRoot().traverse()) {
                if (!(node.getUserObject() instanceof Change change)) {
                  continue;
                }
                for (boolean selected : List.of(false, true)) {
                  tree.includeChange(change);
                  var component =
                      cellRenderer.getTreeCellRendererComponent(
                          tree, node, selected, true, true, 0, selected);
                  var text = textRenderer(component);
                  var revision =
                      change.getAfterRevision() != null
                          ? change.getAfterRevision()
                          : change.getBeforeRevision();
                  String nativeText = text.getCharSequence(false).toString();
                  if (!nativeText.startsWith(revision.getFile().getName())) {
                    throw new AssertionError(
                        "Native filename or search text was changed: " + nativeText);
                  }
                  if (!nativeText.matches(".*(?:\\+\\d+  −\\d+|\\[二进制\\])$")) {
                    throw new AssertionError("Missing trailing statistics: " + nativeText);
                  }
                  if (!text.getAccessibleContext()
                      .getAccessibleName()
                      .contains(revision.getFile().getName())) {
                    throw new AssertionError("Screen reader cannot access statistics");
                  }
                  var checkBox = findCheckBox(component);
                  if (checkBox == null
                      || !checkBox.isVisible()
                      || checkBox.getState() != ThreeStateCheckBox.State.SELECTED) {
                    throw new AssertionError("Native checkbox lost its selected state");
                  }
                  tree.excludeChange(change);
                  component =
                      cellRenderer.getTreeCellRendererComponent(
                          tree, node, selected, true, true, 0, selected);
                  if (findCheckBox(component).getState() != ThreeStateCheckBox.State.NOT_SELECTED) {
                    throw new AssertionError("Native checkbox cannot be cleared");
                  }
                }
              }
              var rootComponent =
                  cellRenderer.getTreeCellRendererComponent(
                      tree, tree.getRoot(), false, true, false, 0, false);
              var rootText = textRenderer(rootComponent);
              if (rootText.getCharSequence(false).toString().contains("−")) {
                throw new AssertionError("Directory row inherited file statistics");
              }
            });
    System.out.println(
        "PASS: trailing statistics, native text, selection, checkboxes and accessibility");
  }

  /**
   * 定位原生复合渲染器中负责包含/排除文件的复选框。
   *
   * @param component 原生渲染组件
   * @return 原生三态复选框，缺失时返回空
   */
  private static ThreeStateCheckBox findCheckBox(Component component) {
    if (component instanceof ThreeStateCheckBox checkBox) {
      return checkBox;
    }
    if (component instanceof Container container) {
      for (Component child : container.getComponents()) {
        var checkBox = findCheckBox(child);
        if (checkBox != null) {
          return checkBox;
        }
      }
    }
    return null;
  }

  /**
   * 导出真实 Swing 文件树的绘制结果。
   *
   * @param tree 测试树
   * @param target PNG 输出位置
   * @throws Exception 绘制或文件写入失败时抛出
   */
  private static void render(ChangesTree tree, Path target) throws Exception {
    Files.createDirectories(target.getParent());
    BufferedImage image = new BufferedImage(920, 260, BufferedImage.TYPE_INT_RGB);
    ApplicationManager.getApplication()
        .invokeAndWait(
            () -> {
              var graphics = image.createGraphics();
              graphics.setColor(java.awt.Color.WHITE);
              graphics.fillRect(0, 0, 920, 260);
              int row = 0;
              for (var node : tree.getRoot().traverse()) {
                if (node.getUserObject() instanceof Change) {
                  var rendered =
                      tree.getCellRenderer()
                          .getTreeCellRendererComponent(tree, node, false, true, true, row, false);
                  var text = textRenderer(rendered);
                  text.setSize(890, 32);
                  var rowGraphics = graphics.create(15, 15 + row * 36, 890, 32);
                  text.paint(rowGraphics);
                  rowGraphics.dispose();
                  row++;
                }
              }
              graphics.dispose();
            });
    ImageIO.write(image, "png", target.toFile());
  }
}
