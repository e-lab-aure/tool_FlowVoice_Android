# FlowVoice Android

Push-to-talk voice transcription overlay for Android. Press and hold the floating mic button in any app, speak, release - the transcribed text is injected directly into the active text field.

## Requirements

### Server

FlowVoice Android requires **[tool_transcript_Lex-IA](https://github.com/e-lab-aure/tool_transcript_Lex-IA)** running on your local network. The server handles speech recognition via Whisper and exposes the API that this app calls.

Follow the setup instructions in that repository to get the server running before using this app.

### Device

- Android 8.0 (API 26) or higher
- Microphone
- Network access to the Lex-IA server (same Wi-Fi or VPN)

## Setup

### 1. Install the app

Build and install via Android Studio, or use the release APK.

### 2. Grant permissions

Open FlowVoice and follow the on-screen prompts:

- **Microphone** - required to capture audio (runtime dialog)
- **Display over other apps** - required for the floating button (tap "Grant overlay permission" to open system settings)
- **Accessibility service** - required to detect text fields and inject text (tap "Enable accessibility service" and enable FlowVoice in the list)

### 3. Configure the server

In the settings screen:

| Field | Description |
|---|---|
| Server host | IP address or hostname of the machine running Lex-IA (e.g. `192.168.1.10`) |
| Port | Port the server listens on (default: `5000`) |
| Language | Transcription language, or `Auto-detect` |
| Noise reduction | Enable DSP preprocessing for noisy environments (adds ~0.5s latency) |

Tap **Test connection** to verify the server is reachable. The language list will be populated from the server on success.

### 4. Use

1. Tap any text field in any app
2. The floating blue mic button appears - drag it anywhere on screen if needed
3. **Press and hold** the button to start recording (button turns red)
4. **Release** to transcribe - the text is inserted into the field automatically

## How it works

```
Press and hold mic
       |
   AudioRecord (16 kHz, mono, PCM 16-bit)
       |
   WAV encoded in memory
       |
   POST /api/transcribe-live  →  Lex-IA server (Whisper)
       |
   transcript
       |
   AccessibilityNodeInfo.ACTION_SET_TEXT
       |
   Text appears in the active field
```

## Known limitations

- **Chrome and web views** - text injection via accessibility may not work. The transcript is automatically copied to the clipboard as a fallback.
- **Password fields** - Android blocks accessibility services from interacting with password inputs by design.
- **Keyboard overlap** - the button defaults to the top-left corner. Drag it above the keyboard if needed.

## Server API reference

The app uses two endpoints from [tool_transcript_Lex-IA](https://github.com/e-lab-aure/tool_transcript_Lex-IA):

**`POST /api/transcribe-live`** - transcribe audio

```
Content-Type: multipart/form-data
Fields:
  audio       WAV file (16 kHz, mono, PCM 16-bit)
  language    Language code or "auto" (default: "auto")
  preprocess  "true" / "false" (default: "false")

Response:
  { "transcript": "...", "language": "fr", "duration_secs": 4.2 }
```

**`GET /languages`** - list supported languages (used by "Test connection")

```
Response:
  { "auto": "Auto-detect", "fr": "French", "en": "English", ... }
```

## Build

```bash
# Debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug
```

Requires Android Studio Hedgehog (2023.1) or later, JDK 17.
