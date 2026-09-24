# MARK-LIV current patch notes

This file replaces the obsolete notes for the former GUI/CLI/background architecture.

## Current baseline

- Headless server lifecycle with `--start`, `--stop`, `--enable`, `--disable`, and `--pair`.
- Native Android and Windows/Linux/macOS companions are the interaction surfaces.
- Interactive voice is companion-only; the server never plays local TTS.
- Origin-first routing sends device-local actions back to the companion that originated the turn unless another target is explicit.
- `origin_device_id` and `active_voice_device` are independent state.
- Paired-device name resolution prefers the online matching record and canonical device UUID.
- Android Accessibility UI automation supports inspect/click/text/scroll/global actions and uses inspect/act/verify recovery.
- Gemini Live transport/session rollover preserves active conversation context.
- News, time, and briefings are on-demand by default.
- User-created recurring workflows persist and execute only when authorized by the user.
- Self-repair is currently Diagnostic/Dry-Run only: read-only, dependency-aware, and without an arbitrary total file-count limit.

## Self-repair safety

Diagnostic mode can inspect the full relevant dependency path and propose a root-cause patch. It cannot modify/delete source, install dependencies, restart the service, or mutate Git. Production apply/rollback is intentionally not enabled yet.

## Validation expected for release packages

At minimum, compile changed Python modules with `python -m py_compile` and run ZIP integrity verification after packaging. Android/desktop source should only be reported as changed when its files actually differ; Android build success must not be claimed unless Gradle was actually run.
