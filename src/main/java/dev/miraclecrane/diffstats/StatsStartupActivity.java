package dev.miraclecrane.diffstats;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;

/**
 * 在项目打开后启用原生差异树统计。 不创建工具窗口，也不增加侧边栏入口。
 *
 * @author MiracleCrane
 * @version 2026-11-02
 */
public final class StatsStartupActivity implements StartupActivity.DumbAware {
  /** {@inheritDoc} */
  @Override
  public void runActivity(Project project) {
    project.getService(StatsService.class);
  }
}
