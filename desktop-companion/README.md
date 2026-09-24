# MARK-LIV Desktop Companion

Native companion runtime for Windows, Linux, and macOS. It is the desktop user-facing control/execution surface for the headless MARK-LIV server and carries the established local action runtime so device-local commands execute on this computer.

## Install and run

```bash
python desktop-companion/install.py
python desktop-companion/companion.py
```

Pair the companion with a running server using a Pair Code created by:

```bash
python main.py --pair
```

## Routing

A command originating from this companion defaults to this computer unless the user explicitly targets another paired device or the server. Cross-device requests use the trusted device mesh. Interactive voice input/output also stays on the companion; the server does not use a local microphone or speaker.

## Local runtime

`local_runtime.py` and `runtime/` preserve desktop-side action support. Android-only `android.ui.*` behavior is not copied to desktop. Platform-specific actions remain subject to OS capabilities and permissions.
