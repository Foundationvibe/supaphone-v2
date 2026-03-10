# SupaPhone Skills

## Extension Skills

- MV3 popup + service worker architecture.
- Backend-backed 6-digit pairing code and QR sync.
- URL vs phone payload classification for quick send/context menu.
- Device lifecycle actions: refresh, rename, remove, reset identity.
- Store package generation from tracked source.

## Android Skills

- Kotlin + Jetpack Compose screen flow.
- Pairing via 6-digit code and QR scan.
- Permission handling:
  - notifications and call during first-boot onboarding
  - camera on-demand from QR tab
- Notification chooser execution for phone and link payloads.
- App Open Ad gating for launcher-only startup.
- Inline banner integration in stable app surfaces.
- Region-aware phone normalization for WhatsApp routing.

## Backend Skills

- Supabase migration lifecycle and schema hardening.
- Edge-function implementation for pairing, send, ack, and reads.
- Firebase push integration for Android delivery.
- Request guards:
  - anon-key validation
  - request shape and size validation
  - client identity and secret verification
  - throttling and request-event tracking

## Operations Skills

- Hosted Supabase CLI workflow.
- Chrome Web Store package generation and config injection.
- Android release preparation with required local AdMob config.
- Cross-module doc synchronization.
- Cleanup discipline without breaking live behavior.
