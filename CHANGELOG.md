# Changelog

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
