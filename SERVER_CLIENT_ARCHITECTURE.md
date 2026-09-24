# MARK-LIV server / companion architecture

This document describes the current architecture. Historical GUI/CLI/background runtime modes are obsolete and are not supported server access surfaces.

## Headless server

The server owns Gemini Live orchestration, trusted-device routing, persistence/scheduling, server-side actions, and HTTP/WebSocket transport. It has no local microphone, speaker, conversational CLI, or desktop GUI.

Public administrative interface:

```text
python main.py --start
python main.py --stop
python main.py --enable
python main.py --disable
python main.py --pair
```

`--server-worker` is an internal lifecycle flag.

## Companions

Android and desktop companions are the user-facing control/execution surfaces. Voice input/output is companion-only. Desktop companions carry the established local action runtime; Android exposes Android-native capabilities and optional Accessibility UI automation.

## Origin-first routing

Every companion-originated turn has an origin device identity. Unless the user explicitly names another target, device-local actions execute on that origin companion.

Two states must remain separate:

- `origin_device_id`: execution target for the current turn.
- `active_voice_device`: interactive audio destination.

`call_current_device` uses the turn origin. `call_paired_device` is for another explicitly targeted paired device. Device-name resolution prefers an online matching record and should resolve to the canonical paired UUID.

## Voice lifecycle

The server receives/forwards live-session audio but does not render audio. Interactive audio returns to the active voice companion. Command routing must never clear or repurpose voice state. Gemini session rollover/reconnect should preserve active conversational context rather than treating transport rollover as a completed conversation.

## Device mesh and pairing

Paired devices use Ed25519 identities, signed challenge/response, and explicit capabilities. Pairing is available through the configured remote endpoint (`https://auth.kasirdigital.web.id`) and supported local transport. `--pair` creates a short-lived Pair Code on an already-running server.

Cloudflare transport does not replace application-level device authentication or capability authorization.

## Android UI execution

Android Accessibility is opt-in. UI automation follows `inspect -> act -> verify/recover`. Blind scrolling/clicking is not the default strategy. A failed UI operation is not evidence by itself that Internet connectivity or Accessibility is unavailable.

## Scheduling/background behavior

There is no default morning briefing, news poll, or time announcement. News/time are fetched on demand. The scheduler exists to execute workflows explicitly created by the user. Recurring workflow state is stored under `~/.jarvis/scheduled_workflows.json`.

## Read-only self repair

Diagnostic self-repair is user-initiated and conversational first: a vague error observation does not trigger it. JARVIS obtains a concrete symptom and explicit diagnostic/repair intent, announces the read-only diagnostic, and only then starts inspection. A code-level activation guard rejects accidental generic calls. Once authorized, diagnostic self-repair may traverse the complete relevant dependency path without a fixed total file limit. It cannot apply edits, delete source, install packages, restart services, or perform Git mutations. Architecture invariants above are part of its diagnostic safety boundary.


### v27 runtime stability
- Headless server does not emit unsolicited CPU/RAM voice alerts; system status remains available on demand.
- Gemini side/diagnostic calls no longer open extra Live sessions that can consume Live quota or destabilize the interactive companion voice session.
- Removed retired pinned `gemini-2.5-flash` / `gemini-2.5-flash-lite` fallback names in favor of maintained rolling aliases.
- WebSocket keepalive/close timeouts are treated as transport rollover: conversation context is preserved and the server reconnects quietly.
- Diagnostic self-repair remains read-only and still requires explicit, concrete user diagnostic intent.
