# Mark-LIV three-mode + trusted device mesh patch

## Implemented
- Runtime modes: `python main.py` / `--ui` (default), `--cli`, `--background`.
- Qt import is lazy, so CLI/background no longer require the desktop UI to start.
- Shared `JarvisLive` brain remains the single execution core in all three modes.
- CLI supports text commands and human confirmation with `/confirm` and `/cancel`.
- Background mode fails closed for irreversible actions because it has no human confirmation surface.
- Persistent Ed25519 device identity and trusted-device registry under `devices/`.
- One-time, expiring signed pairing offers and proof-of-private-key pairing acceptance.
- Explicit per-device capability permissions, revoke, last-seen tracking.
- Authenticated device WebSocket with signed challenge on every connection.
- Bidirectional mesh: paired nodes can submit `jarvis.command`; JARVIS can invoke permitted remote capabilities using `capability.call` / `capability.result`.
- Gemini core tools `list_paired_devices` and `call_paired_device` route through the same mesh.
- Existing browser Remote Dashboard is preserved for compatibility; the trusted node mesh is added alongside it for native/companion agents.
- Device private identity and trust state are gitignored.

## Pairing protocol for a companion node
1. Existing authenticated dashboard session requests `POST /api/pairing/offer`.
2. New node receives `code` + `nonce`, signs UTF-8 `nonce:code` with its Ed25519 private key.
3. Node submits `POST /api/pairing/accept` with `code`, `peer={device_id,name,public_key}`, signature and requested capabilities.
4. Node connects `/ws/device?device_id=...`, receives a random challenge and signs the challenge bytes.
5. Once `ready`, either side can communicate according to the stored capabilities.

## Verification performed
- `python -m py_compile main.py core/interfaces.py core/device_mesh.py dashboard/server.py core/confirm.py core/action_loader.py`
- Device identity, signed pairing, permission allow/deny and revoke test passed.

## Security behavior
Pairing establishes identity, not blanket permission. A trusted device can only use capabilities explicitly stored for it. Irreversible local actions continue to use `core.confirm`; background mode cannot bypass the human gate.

## First-run setup and end-to-end browser pairing
- `python main.py --setup` now configures the Gemini key without opening Qt.
- CLI automatically offers first-run setup when configuration is missing.
- Background mode remains non-interactive and points to `--setup` if configuration is absent.
- Remote Control QR now opens the cryptographic pairing flow rather than creating a disposable browser-only login.
- A paired browser creates and keeps its own Ed25519 private key locally, proves possession on every WebSocket reconnect, and is stored in JARVIS's trusted-device registry.
- Paired browser capabilities: `jarvis.command`, `notification`, `vibration`, `clipboard.write`, and `open_url` (subject to browser/OS permission support).
- Returning paired browsers reconnect using their durable identity without rescanning a QR.
- Full OS-level phone control still requires a native companion agent because browsers cannot expose arbitrary Android/iOS system controls.

## V3 — native Android companion
- Added `android-companion/`, a native Kotlin companion using the same Ed25519 trust registry and device WebSocket.
- Pairing QR now carries the JARVIS device id/public key as an out-of-band trust anchor.
- Browser pairing page offers **OPEN JARVIS COMPANION** via a `jarvis://pair` deep link.
- Device WebSocket challenge now includes a JARVIS signature over `device_id:challenge`, allowing the native companion to authenticate the server at the application layer even with a locally generated TLS certificate.
- Android capabilities currently implemented: notifications, vibration, clipboard write, URL open, app launch, and commands back to JARVIS.
- Fixed invalid-API-key recovery so CLI/background no longer depend on the Qt `_win` object. CLI can re-run terminal setup; background fails closed with the setup command.


## V4 Android Accessibility
- Added an explicit user-enabled AccessibilityService to the native Android companion.
- Added android.ui.inspect/click/text/scroll/global paired capabilities.
- The service fails closed unless the user enables it in Android Settings.
- No root, ADB/Shizuku, Device Owner, or arbitrary shell capability was added.

## V5 — cloud Android builds and release signing
- Added GitHub Actions cloud build for Debug and signed Release APKs.
- Release signing reads the keystore and credentials exclusively from GitHub Actions Secrets.
- Added `apksigner` verification before publishing the Release artifact.
- Added Android signing/build exclusions to `.gitignore`.
- Added `android-companion/SIGNING.md` setup instructions.

## V6 — Cloudflare remote transport
- Optional Cloudflare Quick Tunnel transport is installed by setup; no separate cloudflared install is required.
- The tunnel is outbound-only and requires no router port-forwarding.
- Pairing QR automatically uses the public HTTPS/WSS tunnel URL when available, otherwise LAN.
- Cloudflare is transport only: Ed25519 device pairing, mutual server/device proof, capability permissions and revoke remain enforced by JARVIS.
- Android Companion needs no Cloudflare/Tailscale/VPN application; it connects directly to the HTTPS/WSS endpoint encoded by JARVIS.
- Quick Tunnel URLs are ephemeral across JARVIS restarts. Existing paired devices can pair/reconnect using a fresh QR when the endpoint changes. A stable named Cloudflare Tunnel can be added later without changing the device protocol.

## V7 — Permanent Cloudflare Named Tunnel
- Remote endpoint is fixed to `https://auth.kasirdigital.web.id`.
- Quick Tunnel / random `trycloudflare.com` URLs are no longer used.
- Setup downloads `cloudflared` and securely prompts for a remotely-managed Tunnel Token.
- The token is stored only in git-ignored `config/remote_access.json` (or can be supplied with `JARVIS_CLOUDFLARE_TUNNEL_TOKEN`).
- Cloudflare dashboard Public Hostname must map `auth.kasirdigital.web.id` to `http://localhost:8000`.
- JARVIS device pairing and capability authorization remain independent from Cloudflare transport.

## 2026-09-22 Cloudflare 502 + Android companion stability/UI
- Port 8000 is now always HTTP so Cloudflare Public Hostname service `http://127.0.0.1:8000` matches the JARVIS origin protocol. Public `auth.kasirdigital.web.id` remains HTTPS at Cloudflare edge.
- Existing self-signed LAN HTTPS is retained on port 8001.
- Android pairing now handles HTTP/502/non-JSON responses without crashing.
- Android WebSocket message parsing is guarded against malformed payloads.
- Android companion UI redesigned around the compact dark JARVIS/Hermes-style orb layout while retaining Pair Code, command, Accessibility and all existing native capabilities.

## v9 — Android UI click + live-session rollover continuity
- Android accessibility click canonicalizes escaped view IDs and falls back to an accessibility gesture at the matched node bounds when neither the node nor a parent exposes ACTION_CLICK.
- Android UI inspection now includes node bounds for more reliable follow-up interaction.
- A rejected Gemini Live resumption handle no longer discards the active conversation: the in-RAM transcript is injected into the replacement session as rollover context.
- Transport/session reconnects no longer clear the active transcript by treating every reconnect as an end-of-conversation summary event.
- Desktop companion voice/capability paths were reviewed for this change; no Android-only UI executor behavior was copied to desktop because desktop does not expose android.ui.* capabilities.
