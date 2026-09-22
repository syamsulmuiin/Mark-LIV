# JARVIS Android Companion

Native paired-device endpoint for Mark-LIV. It uses the same Ed25519 identity and `/ws/device` protocol as the browser companion, but can expose Android capabilities that a web page cannot.

## Build
Open `android-companion/` in Android Studio (JDK 17) and build/install the `app` module. The project intentionally does not commit a Gradle wrapper binary.

## Pair
1. Start JARVIS and press **Remote Control** to display the pairing QR.
2. Scan it on Android. The browser pairing page contains **OPEN JARVIS COMPANION**.
3. Tap it and approve opening the companion. The app receives the LAN server, one-time code, and JARVIS public-key trust anchor.
4. The phone generates its own Ed25519 identity, pairs once, then reconnects with signed challenge-response.

The companion verifies `device_id:challenge` signed by the JARVIS identity from the QR. This application-level proof is important because Mark-LIV generates a local/self-signed HTTPS certificate.

## Native capabilities
- `jarvis.command` — phone -> JARVIS command
- `notification` — show Android notification
- `vibration` — vibrate
- `clipboard.write` — set Android clipboard
- `open_url` — open URL with Android intent
- `app.launch` — launch an installed app by Android package name
- `android.ui.inspect` — inspect the active accessibility tree
- `android.ui.click` — click a visible element by text or view ID
- `android.ui.text` — enter text into an editable field
- `android.ui.scroll` — scroll the active UI
- `android.ui.global` — back/home/recents/notifications/quick settings

## Android UI control
Tap **ENABLE ANDROID UI CONTROL** and explicitly enable JARVIS Companion in Android's Accessibility settings. Android owns this consent screen; the app cannot silently enable itself. If the service is disabled, all `android.ui.*` calls fail closed. This does not add arbitrary shell, root, Device Owner, or unrestricted filesystem access.

Android deliberately does not grant ordinary applications arbitrary shell, other-app UI control, protected settings, or unrestricted filesystem access. The V4 companion now supports user-approved Accessibility for UI interaction. Device Owner, Shizuku/ADB, root, arbitrary shell, protected settings, and unrestricted filesystem access remain outside this permission and are not silently requested.
