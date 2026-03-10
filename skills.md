# SupaPhone Skills

## Extension Skills

- MV3 popup + service worker architecture.
- Backend-backed pairing code and paired-device sync.
- QR rendering from active pairing code.
- URL vs phone payload classification for quick send/context menu.
- Device lifecycle actions: rename/remove/refresh.
- Local log management and clearing.

## Android Skills

- Kotlin + Jetpack Compose app navigation and screen flow.
- Pairing via 8-digit code and QR scan (CameraX + ML Kit).
- Permission handling:
  - notifications/call at first-boot path
  - camera on-demand from QR tab
- Notification action handling for call + WhatsApp.
- Region-aware phone normalization for WhatsApp routing.
- Home/History backend data flow and action UX.

## Backend Skills

- Supabase migration lifecycle and schema hardening.
- Edge-function implementation for pairing, send, ack, and reads.
- Firebase data push integration for Android.
- Request guards:
  - anon-key validation
  - request shape/size validation
  - client identity + secret verification
  - burst throttling

## Operations Skills

- Hosted Supabase CLI workflow (linked project path).
- Structured verification before release pushes.
- Cross-module doc synchronization (`README/context/guide/skills/agents`).
- Cleanup discipline for dead code/dependencies without breaking core flows.
