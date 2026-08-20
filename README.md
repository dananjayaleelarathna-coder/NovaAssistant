# Nova Assistant — Android AI Voice Assistant

A customizable, privacy-conscious Android voice assistant built with Kotlin + Jetpack Compose.
Supports English, Sinhala, and Sinhala-English mixed commands; controls flashlight, apps,
calls, alarms, media, volume, and connectivity settings using **only official Android APIs**
— no root, no hidden hacks.

## What's actually implemented in this project

This is a real, compiling-shape Android Studio project (not just mockup screens) with working
logic behind the features below. **It has not been built or run on-device by me** — my
environment can't reach Google's Maven repo to run a Gradle/Android build — so treat this as a
strong, functional starting point that needs a build-and-debug pass in Android Studio, not a
finished shrink-wrapped app. Given how large the original spec is (37 feature areas), I focused
on making the *core pipeline* fully real rather than stubbing everything shallowly:

- **Wake word**: `WakeWordEngine`/`WakeWordService` — foreground service, cycles the on-device
  `SpeechRecognizer`, checks for the assistant's name locally, launches the app on detection.
- **Voice pipeline**: `VoiceEngine` (STT) + `VoiceOutput` (TTS) — real `SpeechRecognizer` /
  `TextToSpeech` wrappers, Kotlin Flow-based.
- **AI abstraction**: `AIProvider` interface + `AIProviderRegistry` + `AnthropicProvider` as one
  concrete example. Swap/add providers without touching any other code — the vendor is never
  hard-coded into UI or intent logic.
- **Offline-first command parsing**: `OfflineCommandParser` — regex/keyword rules covering
  flashlight, camera, calls, alarms/timers, media, volume, battery, time/date, screen actions,
  settings shortcuts, and app launching, understanding Sinhala-English mixed phrasing like
  *"torch eka on karanna"*. This runs with zero network access.
- **Device control**: `DeviceController`, `AppLauncher`, `CallController`, `MessageController`,
  `AlarmController`, `MediaController` — real `CameraManager`, `AudioManager`, `BatteryManager`,
  `AlarmClock` intents, `ContactsContract`, `PackageManager`, media-key dispatch. Where Android
  (10+/13+) blocks direct control (Wi-Fi, Bluetooth, mobile data), it **opens the correct
  Settings screen instead of pretending to succeed**.
- **Confirmation flow**: sensitive actions (calls, messages, cancelling alarms) go through a
  yes/no confirmation before executing, per the spec.
- **Memory**: Room database for facts, custom commands, and conversation history, with
  view/delete/clear-all UI.
- **Settings & Privacy screens**: assistant name, wake word, sensitivity, background listening,
  battery-saving mode, language, personality, AI provider + API key (stored in
  `EncryptedSharedPreferences`, never hard-coded), offline mode, memory toggle, and a Privacy
  screen showing live permission status with data-clearing controls.
- **UI**: animated orb (Canvas + Compose animation), conversation bubbles with action status
  ("✓ Flashlight ON"), quick-action chips, mic + text input, dark/light/AMOLED theming.

## What's intentionally partial / needs follow-up work

- **Custom automations** ("Good morning Nova" → multi-step routine): the data model
  (`CustomCommand`) and storage exist; the UI to *create/edit* routines and the runner that
  executes a stored action sequence are not built yet.
- **Accessibility-driven actions** (screenshot, go home/back, recent apps, lock screen, tap/swipe):
  `NovaAccessibilityService` has the real gesture/global-action code, but `IntentExecutor`
  currently tells the user this needs Accessibility enabled rather than invoking it — wiring
  that last connection (plus the required onboarding/rationale screen) is the next step.
  Never call it silently.
- **Notification reading**: `NovaNotificationListener` collects summaries once granted, but
  `IntentExecutor` doesn't yet route "do I have any messages?" to it.
- **SMS sending**: goes through `ACTION_SENDTO` (opens the SMS app pre-filled) by default,
  which is the safer, always-available path; direct silent `SmsManager.sendTextMessage` is
  implemented but not wired in — you'd want an explicit extra confirmation step before enabling it.
- **Weather, calculator, unit/currency conversion, notes, QR scanner, OCR, translate**, and the
  rest of the "Extra Features" list (#35) aren't built — each is a reasonably scoped addition
  once the core loop is validated on-device.
- **Wake-word reliability**: cycling `SpeechRecognizer` for always-on wake detection works but is
  not as robust or battery-efficient as a dedicated wake-word engine (e.g., Porcupine); the
  sensitivity/battery-saving settings are there but the actual detection quality should be
  tuned on a real device.

## Project structure

```
app/src/main/java/com/nova/assistant/
  engine/       VoiceEngine, WakeWordService, AIProvider abstraction, Accessibility & Notification services
  intent/       CommandIntent enum, OfflineCommandParser, IntentExecutor
  device/       DeviceController, AppLauncher, CallController, AlarmController, MediaController
  data/         SettingsRepository (DataStore), SecureKeyStore (encrypted), MemoryDatabase (Room)
  ui/           ConversationViewModel (orchestrator), NovaApp (nav), screens/, components/, theme/
```

## Setup

1. Open the project root in Android Studio (Koala/2024.1+ recommended).
2. Let Gradle sync — it needs internet access to `google()` and `mavenCentral()` (this sandbox
   couldn't reach those, so this hasn't been synced/built yet on my end).
3. Run on a device or emulator with **API 26+**.
4. In-app: go to Settings → paste an Anthropic API key (or wire up your own `AIProvider`) →
   grant microphone permission when prompted.
5. Grant Contacts/Call/SMS/Location permissions only when you first use a command that needs
   them — the app requests them lazily, not all at launch.

## Permissions and why each is requested

See `AndroidManifest.xml` — every permission has an inline comment explaining what it's for.
Accessibility and Notification access are **never requested automatically**; they're opt-in
from Settings with an explanation first, per Android's own guidelines and the privacy
requirements in the spec.

## Known Android restrictions this app respects (not fakes)

- Wi-Fi/Bluetooth cannot be silently toggled on Android 10+/13+ — the app opens the relevant
  Settings screen and says so out loud, rather than claiming success.
- Mobile data cannot be toggled by any non-system app — always opens Settings.
- No root usage anywhere.
