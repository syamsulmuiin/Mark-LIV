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

## v26 — conversational self-repair activation

- A vague error observation no longer authorizes `self_repair_diagnostic` automatically.
- JARVIS must acknowledge the issue and obtain a concrete symptom plus explicit diagnose/check/debug/repair intent before starting the read-only diagnostic.
- `main.py` enforces the activation rule in code as a backstop, so an accidental model tool call is rejected safely and returned to conversation instead of starting diagnosis.
- Diagnostic mode remains read-only with no apply/edit/delete/install/restart/Git capability.


### v27 runtime stability
- Headless server does not emit unsolicited CPU/RAM voice alerts; system status remains available on demand.
- Gemini side/diagnostic calls no longer open extra Live sessions that can consume Live quota or destabilize the interactive companion voice session.
- Removed retired pinned `gemini-2.5-flash` / `gemini-2.5-flash-lite` fallback names in favor of maintained rolling aliases.
- WebSocket keepalive/close timeouts are treated as transport rollover: conversation context is preserved and the server reconnects quietly.
- Diagnostic self-repair remains read-only and still requires explicit, concrete user diagnostic intent.

## v28 — quiet expected Live rollover tracebacks

- Suppresses the duplicate Python traceback emitted inside `_receive_audio()` for expected Gemini Live rollover conditions (`1008 operation was aborted`, GoAway/session-duration rollover, keepalive ping timeout, and close timeout).
- The exception is still re-raised to the existing lifecycle handler, so reconnect and conversation-context recovery are unchanged.
- Unexpected receive exceptions still print their traceback for debugging.
- Offline/connect failures such as Windows `ConnectionRefusedError` are not reclassified by this patch.

## v29 — quiet transient network recovery
- Expected Gemini Live 1008/GoAway rollover no longer prints a receive-side error line or traceback; lifecycle reconnect remains unchanged.
- Transient network failures (including Windows 1225 refused and 1236 aborted) no longer dump repeated tracebacks while offline; retry/backoff remains active.
- Unexpected/non-network exceptions still print full tracebacks for diagnostics.

## v30 — runtime modularization, centralized model config, bounded logs

- Reduced `main.py` from 2,548 to about 2,063 lines without changing Live-session behavior.
- Moved headless server lifecycle/admin CLI helpers to `core/server_lifecycle.py`.
- Moved Live-bound tool schemas to `core/live_tools.py`; file-backed actions remain auto-discovered from `actions/*.py`.
- Added `core/model_config.py` as the server-side source of truth for Gemini model identifiers. Optional environment overrides: `MARK_LIV_LIVE_MODEL`, `MARK_LIV_TEXT_MODEL`, `MARK_LIV_TEXT_FALLBACK_MODEL`.
- Kept the desktop companion standalone by mirroring the same model-config module inside its packaged runtime; Android does not embed Gemini model identifiers.
- Added `core/runtime_log.py`. The server worker now owns `runtime/error.log` and rotates it at 5 MiB with five backups by default instead of allowing one file to grow forever. Optional overrides: `MARK_LIV_LOG_MAX_BYTES` and `MARK_LIV_LOG_BACKUPS`.
- The launcher no longer leaves an inherited Windows file handle on `error.log`, allowing atomic rollover while the worker is running.
- No user-facing features, routing behavior, voice behavior, reconnect policy, or scheduling cadence were changed.

## v31 — Optional dashboard dependency startup fix
- Dashboard import/initialization failures no longer terminate the core JARVIS runtime.
- Missing optional dashboard dependencies now disable the dashboard and allow the core runtime to continue.
- Dashboard port ownership conflicts remain fatal intentionally, preserving the single-server-worker protection.
- No companion protocol, voice routing, Gemini lifecycle, scheduling, or tool behavior was changed.

### v32 network configuration centralization
Network endpoints and ports now use `core/network_config.py` as the server source of truth. Defaults remain unchanged, but deployments can override them with `MARK_LIV_PUBLIC_HOSTNAME`, `MARK_LIV_DASHBOARD_PORT`, `MARK_LIV_LAN_HTTPS_PORT`, `MARK_LIV_DISCOVERY_PORT`, and `MARK_LIV_LOCAL_HOST`, or `config/network.json`. The standalone desktop runtime carries the same config module. Android uses `BuildConfig.MARK_LIV_PUBLIC_URL`, set at APK build time from `MARK_LIV_PUBLIC_URL`, so the public endpoint is no longer duplicated in Kotlin.

## v33 — explicit default network config

- Added `config/network.json` to the package as the normal editable network configuration.
- Preserved the existing deployment values: `auth.kasirdigital.web.id`, ports `8000`, `8001`, `37991`, and local host `127.0.0.1`.
- Added the same default JSON to the standalone desktop companion runtime.
- Environment variables are still supported only as optional highest-priority overrides.
- Resolution order: environment override → JSON config → built-in safety default.
- No runtime routing, pairing, voice, dashboard, or reconnect behavior was changed.
