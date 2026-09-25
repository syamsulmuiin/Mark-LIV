# MARK-LIV / JARVIS

MARK-LIV is a headless JARVIS server with native companion clients for Android, Windows, Linux, and macOS. The server owns the Gemini Live session, trusted-device mesh, scheduling, memory, and server-side services; interaction and device-local execution happen through installed companions.

## Current architecture (v24 documentation refresh)

```text
                    MARK-LIV SERVER (headless)
                             |
             +---------------+---------------+
             |               |               |
          Android         Windows        Linux/macOS
          Companion       Companion       Companion
             |               |               |
       Android actions   local actions    local actions
       + interactive     + interactive    + interactive
          voice             voice             voice
```

The server does **not** provide a local microphone, speaker, desktop GUI, or interactive conversation CLI. Supported administrative commands are `--start`, `--stop`, `--enable`, `--disable`, and `--pair`. `--server-worker` is internal.

Device-local requests are **origin-first**: a request received from a companion executes on that originating companion unless the user explicitly targets another paired device or the server. `origin_device_id` controls command routing; `active_voice_device` independently controls the interactive audio destination. Server audio output is not used.

## Install the server

Requirements: Python 3.11+ (tested through 3.13) and a configured Gemini API key.

```bash
python setup.py
```

`setup.py` installs Python requirements, Playwright Chromium/Firefox when available, and runs first-time JARVIS configuration. Existing configuration is preserved.

Then:

```bash
python main.py --start
python main.py --pair
```

Enter the Pair Code in a companion. Pairing is designed to work through the configured public endpoint (`https://auth.kasirdigital.web.id`) as well as supported local transport. A Pair Code is short-lived and does not replace device identity: paired devices use Ed25519 challenge/response and capability permissions.

### Administrative commands

| Command | Purpose |
|---|---|
| `python main.py --start` | Start the detached headless server worker |
| `python main.py --stop` | Stop the tracked server worker |
| `python main.py --enable` | Enable per-user autostart and start the server |
| `python main.py --disable` | Remove autostart; does not silently stop a running server |
| `python main.py --pair` | Create a Pair Code on an already-running server |

## Companion clients

### Android

Source: `android-companion/`. Build with Android Studio/JDK 17. Android provides app launching, URL/clipboard/notification functions, interactive voice, and user-approved Accessibility UI automation (`android.ui.inspect`, `click`, `text`, `scroll`, and global actions). Accessibility must be enabled by the user in Android Settings. No root, ADB/Shizuku, Device Owner, or arbitrary shell access is silently added.

### Windows / Linux / macOS

Source: `desktop-companion/`.

```bash
python desktop-companion/install.py
python desktop-companion/companion.py
```

The desktop companion carries a local runtime so established device-side actions execute on the companion computer instead of turning the headless server into a desktop-control endpoint.

## Voice and command routing

Interactive voice belongs to the companion. The server brokers the Gemini Live session but never plays TTS locally. The companion that originates a voice interaction is the normal response-audio target.

Examples:

- Voice from Android: “Buka WhatsApp” → Android companion.
- Voice from Windows: “Buka Chrome” → Windows companion.
- “Buka Chrome di laptop Linux” → explicitly targeted paired Linux companion.
- A server administration request may execute on the server when that target is explicit and an appropriate server action exists.

`call_current_device` means the companion that originated the current turn. `call_paired_device` is for an explicitly selected other paired device. Name-to-device resolution prefers the currently online matching record.

## Android UI automation

Android UI work should use a state-aware loop rather than blind coordinates or blind scrolling:

```text
inspect -> choose action -> act -> inspect/verify -> recover if needed
```

For example, finding a WhatsApp contact should inspect the active UI, use Search when available, enter the contact name, inspect the result, open the correct chat, find the message field, enter text, send, and verify. A failed click should trigger inspection/recovery rather than an unsupported conclusion that the phone has no Internet or Accessibility is disabled.

## User-created scheduled workflows

MARK-LIV has no built-in morning news/briefing schedule. News, time, and briefings are on-demand unless the user explicitly creates a schedule. User-created recurring workflows are persisted in:

```text
~/.jarvis/scheduled_workflows.json
```

A request such as “Setiap pagi jam 7 sapa aku lalu bacakan berita terbaru” creates a user-authorized recurring workflow. At execution time JARVIS runs the instruction then, allowing time-sensitive content to be fetched fresh. `last_run_date` prevents duplicate daily execution.

## Diagnostic / Dry-Run Self Repair

`actions/self_repair_diagnostic.py` provides **read-only** self-repair diagnosis. It may inspect as many relevant project files as required by the dependency/root-cause path. There is no arbitrary total file-count limit; discovery proceeds in context-sized rounds until the relevant closure is reached.

Diagnostic activation is **conversational first**. A vague statement such as “ada error” does not start self-repair automatically. JARVIS first acknowledges the issue and asks for the concrete symptom (or uses evidence the user already supplied), then runs the diagnostic only when the user explicitly asks to diagnose/check/debug/repair that concrete problem. Before starting, JARVIS states that the diagnostic is read-only. A server-side activation guard rejects accidental generic tool calls as a second safety layer.

Diagnostic mode can report a root cause, files inspected, files that would need changes, proposed changes, validation plan, risk, and missing evidence. It deliberately has **no apply capability**. It cannot use this mode to edit/delete production source, install dependencies, restart the server, or commit/push changes. Saying “terapkan” does not bypass that restriction.

Protected architecture invariants include: headless server operation; origin-first device routing; separation of `origin_device_id` and `active_voice_device`; companion-only voice output; preservation of Android/Windows/Linux/macOS capability paths; remote pairing support; and no unsolicited background news/time/briefing jobs.

## Security model

Pairing establishes device identity, not blanket authority. Device calls are restricted to advertised/permitted capabilities. Secrets, credentials, private device identity, signing material, and runtime state must remain outside version control. Android Accessibility is explicit user consent and fails closed when disabled.

For Android release signing, see `android-companion/SIGNING.md`.

## Project map

- `main.py` — headless server lifecycle, Gemini Live orchestration, routing, scheduling.
- `core/` — Gemini/session, device mesh, setup/configuration, interfaces, memory helpers and shared infrastructure.
- `actions/` — server/tool actions, including scheduled workflows and read-only self-repair diagnostics.
- `dashboard/` — server HTTP/WebSocket transport and pairing/device endpoints; not a replacement for native companions.
- `android-companion/` — Android native companion.
- `desktop-companion/` — Windows/Linux/macOS native companion and local runtime.
- `memory/` — memory/config management.
- `plugins/` — plugin extension points.

## Troubleshooting

**`--start` says already running:** use `python main.py --stop` before intentionally replacing/restarting the worker. Server lifecycle is tracked by the worker PID; HTTP health is auxiliary verification rather than the sole lifecycle source of truth.

**`--pair` says the server is not running:** start it first with `python main.py --start`.

**Android app opens but UI control fails:** confirm JARVIS Companion Accessibility is enabled, then inspect the current Android UI before retrying an action.

**A device appears offline despite another record with the same name being online:** use the current paired-device list/UUID. Routing prefers the online matching device; stale historical records should not be treated as the active target.

**Voice input works but no JARVIS audio returns:** command routing and voice routing are separate. The origin companion should remain the command target while `active_voice_device` remains the audio target; do not merge those states.

## Development rule

Preserve existing behavior unless a change is explicitly requested. Fix root causes with the smallest compatible patch. Device-specific fixes should not be copied to other companion platforms unless their implementation actually requires the same change.


### v27 runtime stability
- Headless server does not emit unsolicited CPU/RAM voice alerts; system status remains available on demand.
- Gemini side/diagnostic calls no longer open extra Live sessions that can consume Live quota or destabilize the interactive companion voice session.
- Removed retired pinned `gemini-2.5-flash` / `gemini-2.5-flash-lite` fallback names in favor of maintained rolling aliases.
- WebSocket keepalive/close timeouts are treated as transport rollover: conversation context is preserved and the server reconnects quietly. Expected Live rollover conditions are logged concisely without the duplicate receive-task traceback; unexpected exceptions retain full tracebacks for diagnosis.
- Diagnostic self-repair remains read-only and still requires explicit, concrete user diagnostic intent.

### Runtime log behavior (v29)
Expected Live-session rollover and temporary network loss are recovered through the normal reconnect path without traceback spam. Unexpected application errors still retain full tracebacks for diagnosis.

### Runtime maintainability

The headless entry point is intentionally thin: server start/stop/enable/disable/pair lifecycle code lives in `core/server_lifecycle.py`, Live-only tool declarations live in `core/live_tools.py`, and provider model identifiers live in `core/model_config.py`. Change the default Gemini model there once, or override it with `MARK_LIV_LIVE_MODEL` / `MARK_LIV_TEXT_MODEL` / `MARK_LIV_TEXT_FALLBACK_MODEL`.

Long-running server output is bounded. `runtime/error.log` rotates by size (5 MiB, five backups by default) through `core/runtime_log.py`. Deployments can change the limits with `MARK_LIV_LOG_MAX_BYTES` and `MARK_LIV_LOG_BACKUPS`.
