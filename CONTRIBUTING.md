# Contributing to Nothing One

Thanks for your interest. This project has one prime directive that shapes every contribution:

> **The app must remain fully local. No INTERNET permission, ever.**

A PR that adds a network dependency, a telemetry hook, or the `INTERNET` permission will be rejected regardless of its other merits.

## Ground rules

1. **No network access — verify before opening a PR.** After your changes, confirm the merged manifest still has no INTERNET permission:
   ```bash
   ./gradlew assembleDebug
   apkanalyzer manifest print app/build/outputs/apk/debug/app-debug.apk | grep -i internet
   # expect: no output
   ```
2. **Keep the accent budget at one.** Nothing Red (`#FF0044`) is the only accent color. If your feature needs a second color, it needs a better design.
3. **Match the NothingOS design language.** Black surfaces, dot-matrix type for states and labels, red-dot prefixes for active items, dot-grid dividers between sections. Reuse `DotMatrixText`, `RedDot`, `DotGridDivider`, `DotMatrixBadge` from `ui/components` before writing new ones.
4. **Keep business logic pure where you can.** State machines (see `PomodoroEngine`) and parsers (see `AssistantTools`) live outside Android classes so they stay unit-testable. Pin behavior with JUnit tests in `app/src/test`.
5. **Respect the originals.** Sibling projects `NothingMusic/` and `NothingJournal/` are upstream sources and must remain untouched; all work happens inside `Nothing-One/`.

## Development workflow

```bash
./gradlew assembleDebug        # build
./gradlew testDebugUnitTest    # unit tests
./gradlew lint                 # android lint
```

Every PR runs build + tests + lint on GitHub Actions; keep it green.

## Commit style

Short imperative subject lines focused on intent, e.g. `Focus: fix long break after skipped block`, `Assistant: show memory card above composer`.

## Reporting bugs

Open an issue with device/Android version, steps to reproduce, and `adb logcat` output. For privacy-sensitive behavior (anything that looks like data leaving the device), mark the title with `[privacy]` — those get priority review.
