## v45 - Operation-aware companion routing

- Extended origin-device isolation from whole-action routing to operation-aware routing.
- Preserved the existing device-local guard for browser, computer, settings, desktop, files, apps, screen, messaging, system-monitor, and YouTube actions.
- Added mixed-action classification for `code_helper`, `game_updater`, and `file_processor`.
- Device-local operations such as open, launch, type, click, press, focus, close, install, update, patch, show, preview, and print are routed to the origin companion.
- Backend-only computation remains available on the server and is not blocked merely because the request originated from a companion.
- Preserved v43 credential-input protection and v44 origin-device isolation.
- No UI feature was added or removed.

## v44 - Origin companion media isolation

- Fixed a routing omission that allowed `youtube_video` to execute on the headless server even when the request originated from a companion.
- Added `youtube_video` to the existing origin-first device-local action guard.
- Companion-originated YouTube/media workflows must continue through `call_current_device` and the origin companion capabilities instead of opening media on the server.
- Preserved the full companion control and credential-input boundary from v43.
- No unrelated runtime, UI, Android application, or scheduling behavior was changed.

## v42 - Clean source package

- Repacked v41 as a clean source distribution.
- Removed generated Python bytecode and `__pycache__` directories from the distributable package.
- Validation now checks Python source syntax without writing bytecode into the source tree before packaging.
- Preserved the v41 signed release workflow fix and the verified GitHub Actions majors.
- Preserved removal of the obsolete headless-server audio modules while retaining the desktop companion audio modules.
- No runtime feature, Android application source, server behavior, or companion behavior was changed.

## v41 - Signed release packaging reliability

- Kept the verified GitHub Actions majors introduced in v40.
- The Android debug job is unchanged because it already succeeds with the new Actions versions.
- The signed release job now uses Gradle cache in read-only mode to avoid writing or reusing release packaging state across runs.
- The signed release build now runs `clean assembleRelease --stacktrace` so stale incremental packaging output is removed and any future packaging failure exposes the full underlying exception.
- No Android application source, Gradle version, SDK level, signing secret names, or runtime behavior was changed.

# v39 — Android CI maintenance and headless server cleanup

- Updated `actions/checkout` in the Android workflow from v4 to v5. Other workflow actions remain on their currently compatible major versions pending verified upstream major releases.
- Removed obsolete server-local audio modules: `core/audio_devices.py`, `core/stt.py`, and `core/tts.py`.
- Preserved the corresponding desktop companion runtime modules under `desktop-companion/runtime/core/`.
- No runtime routing, voice relay, companion state, scheduling, or server administration behavior was changed.

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


## v34 — True headless server dependency split

- Removed the server runtime's top-level `sounddevice` import.
- Removed server-side audio-device configuration and enumeration. Microphone and speaker hardware are companion responsibilities.
- Removed `PyQt6` and `sounddevice` from the root/server `requirements.txt`.
- Kept desktop companion audio dependencies in `desktop-companion/requirements.txt`.
- The server continues to process and relay companion PCM audio without opening local audio hardware.
- No server GUI dependency is imported or installed on the server startup path.
- No files were removed.


## v35 — English-only project text audit

- Replaced remaining Indonesian examples in `readme.md` with English examples.
- Rewrote self-repair examples in `core/prompt.txt` in English.
- Moved non-English diagnostic input aliases out of `main.py` into `core/language_compat.py`.
- Non-English literals in `core/language_compat.py` are intentional compatibility data only; they preserve natural-language command recognition and are not project-facing documentation, comments, logs, prompts, or UI text.
- Runtime behavior, routing, voice, companion execution, and self-repair safety semantics are unchanged.

## v36 — Cross-platform headless server setup

- Added OS and CPU-architecture reporting, including normalized ARM64/aarch64 detection.
- Linux setup now creates and uses a project-local `.venv` when needed, avoiding PEP 668 system-Python installation failures on Debian/Ubuntu/Armbian.
- Reduced root `requirements.txt` to headless server dependencies.
- Moved desktop input, screen, camera, and local-control dependencies to `desktop-companion/requirements.txt`.
- Moved Playwright to the optional `requirements-browser.txt` server extra and stopped automatic browser-binary installation.
- Removed desktop/audio post-install instructions from the server installer.
- No existing source file was removed.

## v37 — Quiet recovery for abnormal WebSocket closure

- Treats Gemini Live WebSocket `1006 abnormal closure` as a transient transport failure when the underlying connection disappears without a close frame.
- Recognizes Windows network failures `WinError 64` and `WinError 121` as transient transport conditions.
- Suppresses duplicate receive-side and TaskGroup tracebacks for these expected connectivity failures while preserving reconnect/backoff and local conversation-context recovery.
- Unexpected application errors still retain full tracebacks.
- No companion routing, audio lifecycle, scheduling, tool behavior, or server administration behavior was changed.


## v38 - Companion Thinking State
- Added an event-driven `THINKING` voice state for Android and desktop companions.
- Voice state flow is `LISTENING -> THINKING -> SPEAKING -> LISTENING`.
- `THINKING` is emitted only when Gemini produces pre-audio model content or while a tool call is being executed; no cosmetic delay timer was added.
- Existing microphone streams remain alive across state changes.
- Preserved the v37 transient network recovery behavior.

## v40 - Verified Android GitHub Actions majors

- Verified the latest upstream releases before changing the workflow: actions/checkout v7.0.1, actions/setup-java v6.0.1, gradle/actions v6.3.0, and actions/upload-artifact v7.0.1.
- Updated `build-android.yml` to the corresponding maintained major tags: `checkout@v7`, `setup-java@v6`, `setup-gradle@v6`, and `upload-artifact@v7`.
- Android application source, Java 17, Android SDK 35, Gradle 8.10.2, signing, and artifact paths are unchanged.
- Python Quality CI remains intentionally on hold.

## v43 - Full companion control with credential boundary

- Companion-origin requests now explicitly continue autonomous device UI execution for ordinary user-authorized actions, including navigation, text entry, selection, Send/Submit, and verification.
- Android Accessibility inspection marks credential fields and Android text entry hard-blocks password/PIN/passcode/credential fields before input.
- Desktop companion local execution blocks operations that explicitly target credential fields or credential data types.
- Password generation through computer control is blocked on both server and desktop runtime copies.
- When authentication is encountered, automation preserves the current session/state and waits for user instruction instead of navigating away or handing ordinary UI work back to the user.
- No credential is typed, pasted, generated, inferred, or submitted by MARK-LIV.
