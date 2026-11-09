# Diff Line Stats

[简体中文](README.zh-CN.md) · [Download](https://github.com/MiracleCrane/idea-diff-line-stats/releases/tag/v0.1.3) · [MIT License](LICENSE)

Show green `+added` and red `−deleted` line counts next to each file in IntelliJ IDEA's **existing diff file tree**, including **Show Diff with Working Tree**. Keep reviewing files in the same place, without opening a separate tool window.

![Native text renderer preview using a synthetic test repository](docs/preview.png)

*Preview rendered from native IDEA text components in the integration test. The surrounding IDE window is not shown.*

## Status and compatibility

**0.1.3** preloads every file in the comparison in the background, including collapsed directories and off-screen rows. Scrolling does not trigger per-row loading; line counts remain after file labels.

**v0.1.3 is an early preview.** Tested against IntelliJ IDEA **2026.3 / IU-263.7307.200** on Windows. The plugin descriptor permits builds `263.7307` through `263.*`; other builds in that range have not been independently tested. This release targets 2026.3; use v0.1.2 for 2026.2. Remote Development is not verified.

The plugin integrates with the platform's Swing changes-tree implementation. Future IDEA updates may require adaptation. It has not been submitted to JetBrains Marketplace and has not yet completed a standalone Plugin Verifier run.

## Install

1. Download **`diff-line-stats-0.1.3.zip`** from the [release page](https://github.com/MiracleCrane/idea-diff-line-stats/releases/tag/v0.1.3). Do not select GitHub's automatically generated source-code archive.
2. In IDEA, open **Settings → Plugins → gear icon → Install Plugin from Disk**.
3. Select the ZIP without extracting it, then restart IDEA.
4. Open **Git branch menu → Show Diff with Working Tree**. Each file label will gain line counts after a brief background calculation.

There is no additional sidebar button or tool window. To uninstall, remove **Diff Line Stats** from **Settings → Plugins → Installed**, then restart IDEA.

## Automatic updates

Updates are distributed through a GitHub custom plugin repository. Do not select JetBrains Marketplace as this plugin's update source.

1. Open **Settings → Plugins → gear icon → Manage Plugin Repositories** and add:

   ```text
   https://raw.githubusercontent.com/MiracleCrane/idea-diff-line-stats/updates/updatePlugins.xml
   ```

2. Select **Diff Line Stats → Additional Info → Updates from** (or the link to choose an update source) and choose that repository.
3. IDEA checks this repository for compatible versions. Automatic installation follows the IDE's existing automatic plugin update setting.

This feed targets IDEA 2026.3. For each release, maintainers must upload the ZIP to GitHub Releases, then update the version, download URL and compatibility range in `updatePlugins.xml` and push it to the persistent `updates` branch. Creating a Release alone does not update this index. Each plugin ID may appear only once per index.

## Counting behavior

- Uses the before/after revisions of each displayed change. Swapping the comparison sides swaps additions and deletions.
- A replaced line normally counts as `+1 −1`. These are separate counts, not a net total.
- Supports added files, deleted files and renames. An unchanged rename is `+0 −0`.
- An empty file has zero lines. Adding/removing the final newline counts as a line replacement. CRLF and LF are normalized; other whitespace changes are counted.
- Uses IDEA's line-diff algorithm. Repeated lines and different diff algorithms can produce counts that differ from Git's default `--numstat` output.
- Repaints on unsaved document edits and VFS updates. Computation runs on two background workers per project, with per-node caching and stale-result rejection.
- `…` means pending. Binary files, oversized files and unavailable revisions show a status instead of a misleading zero. Status labels in this version are Chinese: `[二进制]` (binary), `[超大文件]` (too large), `[统计不可用]` (unavailable).
- Text comparisons are limited to **2 Mi characters and 100,000 lines per side**, plus the platform diff algorithm's own limits. Reopen the comparison to retry an unavailable revision.

All work is local. The plugin does not upload code or execute Git write operations. It reads revisions using the IDE's existing VCS integration.

## Build and test

This release uses an offline build against an existing IDEA installation. Requirements: **Windows, PowerShell 7, and IDEA 2026.3 with its bundled JBR compiler**. Tests also require **Git and Python 3** on PATH.

```powershell
pwsh -File .\build.ps1 -IdeaHome 'C:\path\to\IntelliJ IDEA 2026.3'
pwsh -File .\test.ps1 -IdeaHome 'C:\path\to\IntelliJ IDEA 2026.3'
```

The installable archive is written to `build/diff-line-stats-0.1.3.zip`. Use `-BuildDirectory` to place generated files outside the checkout. Tests use their own Git fixture and IDEA configuration, system and plugin directories. They do not reuse your account or license data.

See [validation details](docs/testing.md). Java sources use Google Java Format 1.32.0 (GOOGLE style).

## Contributing

Bug reports and focused pull requests are welcome. Include the exact IDEA build, comparison type, expected/actual counts and a small synthetic example. Please omit private source code, credentials and proprietary screenshots.

Planned work includes a standard Gradle build, automated compatibility verification, English/localized status labels and Marketplace packaging. Broader compatibility will be claimed only after verification.

## License

[MIT](LICENSE). IntelliJ IDEA and JetBrains are trademarks of JetBrains s.r.o. This is an independent community project and is not affiliated with JetBrains. The release includes only this plugin's code and license, not the IDE or its SDK libraries.
