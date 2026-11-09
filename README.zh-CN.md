# Diff Line Stats

[English](README.md) · [下载安装包](https://github.com/MiracleCrane/idea-diff-line-stats/releases/tag/v0.1.3) · [MIT 许可证](LICENSE)

在 IntelliJ IDEA **原生 Diff 文件列表**中，为每个文件显示绿色 `+新增行数`、红色 `−删除行数`。支持“与工作树比较 / Show Diff with Working Tree”，无需打开额外窗口。

![使用测试仓库生成的 IDEA 原生文字组件预览](docs/preview.png)

*图片来自原生组件集成测试，使用虚构测试文件，未包含整个 IDE 窗口。*

## 当前状态

**0.1.3** 在打开或更新列表时后台预加载所有文件，包括折叠目录和屏幕外的行，不再等滚动才逐行计算。行数显示在文件标签后面。

**v0.1.3 为早期预览版。** 已在 Windows 上使用 **IntelliJ IDEA 2026.3 / IU-263.7307.200** 验证。安装包允许 `263.7307` 至 `263.*`；该范围内的其他版本尚未逐一测试。本版面向 2026.3；2026.2 请使用 v0.1.2。远程开发模式未验证。

插件使用 IDEA 的 Swing 变更树实现，IDEA 更新后可能需要适配。当前未上架 JetBrains 插件市场，也尚未完成独立 Plugin Verifier 检查。

## 安装

1. 从 [Release 页面](https://github.com/MiracleCrane/idea-diff-line-stats/releases/tag/v0.1.3) 下载 **`diff-line-stats-0.1.3.zip`**。GitHub 自动提供的 Source code 压缩包是源码，不能作为插件安装。
2. IDEA → **设置 → 插件 → 齿轮 → 从磁盘安装插件**。
3. 选择 ZIP，不用解压，安装后重启 IDEA。
4. 打开分支菜单的 **Show Diff with Working Tree / 与工作树比较**，后台统计完成后显示数字。

插件没有独立侧边栏图标。卸载时在“插件 → 已安装”中移除 Diff Line Stats，并重启 IDEA。

## 统计规则

- 读取当前列表每项变更的 before/after 版本，交换方向后新增和删除随之交换。
- 修改一行通常记为 `+1 −1`，不是净增行数。支持新增、删除、重命名；纯重命名显示 `+0 −0`。
- 空文件为零行，末尾换行的变化记为一删一增。CRLF/LF 归一化，其他空白变化计入统计。
- 使用 IDEA 的差异算法，重复行等情况下可能与 Git 默认 `--numstat` 的数字不同。
- 未保存的文档编辑和 VFS 更新会触发刷新。每个项目最多同时运行两个后台计算任务，按树节点缓存，丢弃过期计算结果。
- 初次计算显示 `…`；二进制、超大文件、读取失败分别显示 `[二进制]`、`[超大文件]`、`[统计不可用]`。读取失败可重新打开比较列表重试。
- 每侧最多处理 **2 Mi 字符和 100,000 行**，同时遵守平台差异算法自身的限制。

所有处理都在本机进行，不上传代码，不执行 Git 提交、暂存或工作区写入。

## 构建与测试

首版使用本地已安装 IDEA 作为编译依赖。需要 **Windows、PowerShell 7、IDEA 2026.3 自带的 JBR 编译器**；运行测试还需要 PATH 中的 **Git 和 Python 3**。

```powershell
pwsh -File .\build.ps1 -IdeaHome 'C:\path\to\IntelliJ IDEA 2026.3'
pwsh -File .\test.ps1 -IdeaHome 'C:\path\to\IntelliJ IDEA 2026.3'
```

安装包输出在 `build/diff-line-stats-0.1.3.zip`。可用 `-BuildDirectory` 将生成文件放到仓库外。测试使用独立 Git 仓库及 IDEA 配置、缓存、插件目录，不复用个人账户或许可证数据。

验证范围见 [测试说明](docs/testing.md)。Java 源码采用 Google Java Format 1.32.0，GOOGLE 风格。

## 反馈与计划

欢迎提交问题和小范围 PR。请提供 IDEA 完整版本号、比较方式、预期/实际数字，以及不含私有代码的最小示例。

后续计划包括标准 Gradle 构建、自动兼容性检查、状态文字国际化和插件市场发布准备。支持更多版本之前会先验证。

项目使用 [MIT 许可证](LICENSE)。这是独立社区项目，与 JetBrains 无关联；安装包不包含 IDEA 或 SDK 类库。
