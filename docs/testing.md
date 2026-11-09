# Validation

## 0.1.3 platform compatibility — 2026-11-09

Verified against IntelliJ IDEA **2026.3 / IU-263.7307.200** on Windows. The 263 distribution extracts VCS APIs into the bundled Version Control plugin; both the descriptor dependency and compiler/test classpaths now reference it. The isolated launcher uses the default application-info resource instead of the removed Idea-prefixed resource.

All 14 text-boundary checks and the native IDEA integration suite pass, including forward/reversed Git comparisons, unsaved edits, rebuilt trees, directory grouping, checkboxes, accessibility and background preloading of 120 collapsed/off-screen files. Model replacement and incremental insertion remain covered.

This release targets platform 263 only. The previous 0.1.2 artifact remains the release for platform 262.

## 0.1.2 preloading regression — 2026-11-03

Trailing counts are restored. The native integration suite verifies that all 120 files are read in the background while directories are collapsed and a plain renderer provides no statistics text component. No row is scrolled into view to trigger these reads. It also checks complete model replacement and inserting a file into an existing model, without re-reading already cached immutable revisions.

Normal Git comparisons, reversed direction, unsaved edits, rebuilding, directory grouping, selection, checkboxes and trailing-label rendering remain covered. The tree cache observes model changes independently of rendering and does not strongly retain closed tree widgets through Swing model listeners.

## 0.1.0 baseline

Verified on 2026-11-02 using IntelliJ IDEA **2026.2.3 / IU-262.10968.63**, its bundled JBR, PowerShell 7, Git and Python 3 on Windows.

## Automated coverage

The text suite contains 14 checks: empty files, additions, deletions, replacements, reversed comparisons, unterminated final lines, final-newline changes, blank lines, CRLF normalization, Unicode, whitespace, unchanged rename content, binary detection and the text-size limit.

The integration suite starts an isolated IDEA process, loads the actual plugin ZIP contents, opens a synthetic Git repository, calls the platform's Git comparison API and builds the native `SimpleChangesBrowser`. It checks the text produced by the original renderer plus this plugin's wrapper.

Expected and verified examples:

| File | Forward | Reversed |
| --- | --- | --- |
| Added.txt | +3 −0 | +0 −3 |
| Deleted.txt | +0 −2 | +2 −0 |
| Modified.txt | +2 −1 | +1 −2 |
| Renamed.txt | +0 −0 | +0 −0 |
| image.png | Binary status | Binary status |

An unsaved edit updates Modified.txt to `+3 −1`. The suite also checks saved-document cache collection, rebuilt trees, repeated rendering and native selection.

## Boundaries

These are headless native-component integration tests, not a claim of completed manual GUI acceptance on every supported build. Other major IDE versions, other operating systems and split Remote Development are not verified. Plugin Verifier has not yet been run.

The isolated process does not reuse a personal IDEA license or account. Bundled Ultimate-only plugins may report unavailable dependencies in this environment; the Git comparison tests run independently of them.

Run the suite using `test.ps1`; see the root README for requirements. Generated files remain in the selected build directory. The integration test uses a process exit code to report its result and never runs against a user's business repository.
