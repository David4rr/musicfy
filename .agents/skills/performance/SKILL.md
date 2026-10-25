---
name: performance
description: >-
  Use this skill when building UI, animations, blurs, glass effects, lists, or
  player transitions in Musicfy, or when profiling frame times with Perfetto.
---

# Musicfy Performance & Smoothness Guide

Read the detailed notes and measurements in [docs/notes/performance.md](../../../docs/notes/performance.md) alongside Rule 3 in [CLAUDE.md](../../../CLAUDE.md).

## Key Rules

1. Do no heavy parsing, regex, disk, database, or bitmap work during composition; cache off the main thread.
2. Read fast-changing values (sheet progress, playback position, scroll offsets) inside `graphicsLayer`, `drawBehind`, `offset`, or `layout` via `() -> T` lambdas.
3. Wrap looping animations inside `Modifier.damageBoundary()` (from [`GlassKit.kt`](../../../app/src/main/kotlin/com/example/musicfy/ui/component/GlassKit.kt)) when nothing draws outside their bounds, and pause loops when obscured via `LocalAppContentObscured`.
4. Use `DownscaledBlur` and `GlassPillBackground` from [`GlassKit.kt`](../../../app/src/main/kotlin/com/example/musicfy/ui/component/GlassKit.kt) and respect `LocalBlurQuality`.
