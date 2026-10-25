---
name: popup-sheet
description: >-
  Use this skill before building or modifying any popup, bottom sheet, dialog,
  menu sheet, or overlay in Musicfy. Explains PopupSheet, PopupSheetState
  stacking, MenuSheetSurface, surface tokens, and drag physics.
---

# PopupSheet Guide

Read the canonical guide in [docs/popup-sheet.skill.md](../../../docs/popup-sheet.skill.md) before creating or editing any sheet, popup, or dialog.

## Key Rules

1. Use [`PopupSheet`](../../../app/src/main/kotlin/com/example/musicfy/ui/component/PopupSheet.kt) via `LocalMenuState`, `LocalBottomSheetPageState`, or `LocalZoomOutOverlayState`—never hand-roll a new `ModalBottomSheet` or `Dialog` box.
2. Use `MenuSurface` and `MenuRowSurface` from [`MenuSheet.kt`](../../../app/src/main/kotlin/com/example/musicfy/ui/player/menu/MenuSheet.kt) for sheet chrome instead of `MaterialTheme.colorScheme.*` or custom hex colors.
3. Push sub-sheets onto the same `PopupSheetState` (`popup.show(...)`) so the covered sheet recedes in place, and pop with `popup.dismiss()` (or close the whole stack before navigating with `popup.dismissAll()`).
