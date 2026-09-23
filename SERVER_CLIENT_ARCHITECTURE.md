# MARK LIV — Server / Companion architecture

MARK LIV is now split into a headless server and installed native companions.

## Server
The server has no GUI, CLI conversation menu, local microphone, or local speaker. Runtime commands are only:

- `python main.py --start` — start the detached server and print a temporary native-companion Pair Code.
- `python main.py --enable` — install per-user autostart and start the server.
- `python main.py --stop` — stop the server.
- `python main.py --disable` — remove autostart without silently changing other settings.

Browser dashboard/control routes are not a supported access surface. Pairing and control use the authenticated Ed25519 device mesh.

## Clients
- Android: `android-companion/`
- Windows/Linux/macOS: `desktop-companion/`

Voice input/output exists on the active companion only. The server never opens its own microphone or speaker. The companion that sends the current voice/text interaction becomes the response-audio target, so speech is not broadcast to every paired device.

A client can ask MARK LIV to enumerate paired devices and target another paired device through the existing `list_paired_devices` / `call_paired_device` tool path. Server-side actions continue to execute on the server when requested through an authenticated client.

## Pairing
Start the server with `--start`; it prints a six-character Pair Code valid for ten minutes. Enter the server URL and that code in the installed companion.

## Legacy capability parity
Desktop companions carry a local execution runtime containing the established MARK LIV device-side actions. The server routes these through the authenticated `legacy.action` capability. This preserves computer control/settings, desktop, file, browser, screen, messaging, monitoring, and application-launch behavior while keeping the server itself headless.
