# Parked: voice-only experiments

Not compiled. Kept so nothing is lost.

- `ZapHeadset.kt.txt` — first try at a "glasses as Bluetooth headset" mode
  (phone TTS + phone speech recognizer, overlay pill, microphone foreground
  service). Marcus parked it on 2026-10-13: audio-only belongs to a later
  **accessibility / screen-reader mode** ("Somente áudio" in settings), where
  every focused row and button is read aloud while navigating, like TalkBack.
- `core/.../HeadsetFlow.kt` (compiled, tested, unused) — the yes/no voice
  exchange (read → "Reply?" → dictate → read back → "Send?"). Reusable as the
  confirm step of that future mode.
