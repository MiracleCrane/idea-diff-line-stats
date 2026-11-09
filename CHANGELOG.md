# Changelog

## 0.1.3 — 2026-11-09 (preview)

- Support IntelliJ IDEA 2026.3 / IU-263.7307.200.
- Declare the extracted Version Control plugin dependency and compile against its VCS modules.
- Update the isolated test launcher for the 263 application-info resource layout.
- Retain trailing statistics, unsaved-edit refresh and full-tree background preloading; verify all existing regressions on 263.
- Target builds `263.7307` through `263.*`; keep v0.1.2 available for 2026.2.

## 0.1.2 — 2026-11-03 (preview)

- Keep line counts after file labels while calculating every file in the comparison up front.
- Preload all files in the comparison when the tree loads or changes, including collapsed directories and off-screen rows.
- Refresh cached statistics after edits, model replacement and incremental node changes without waiting for scrolling.
- Retire outdated work and preserve native selection, checkboxes and directory grouping.

## 0.1.0 — 2026-11-02 (preview)

- Add per-file added/deleted line counts to the native IDEA diff tree.
- Read the revisions used by the current comparison, including reversed comparisons.
- Support additions, deletions, unchanged renames and unsaved document edits.
- Run statistics in the background and discard stale results.
- Report binary, oversized and unavailable files without inventing counts.
- Include 14 text-boundary checks and isolated native IDEA integration tests.
- Publish source under the MIT license with English and Chinese documentation.

Verified IDE: IntelliJ IDEA 2026.2.3 / IU-262.10968.63 on Windows. This preview has not been submitted to JetBrains Marketplace.
