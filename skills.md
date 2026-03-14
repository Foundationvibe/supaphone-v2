# SupaPhone Skills

## Extension Skills

- MV3 popup and service-worker architecture
- backend-backed 6-digit pairing code and QR sync
- URL vs phone payload classification for quick send and context menu
- device lifecycle actions: refresh, rename, remove, reset identity
- store package generation from tracked source

## Android Skills

- Kotlin + Jetpack Compose screen flow
- pairing via 6-digit code and QR scan
- permission handling:
  - notifications and call during first-boot onboarding
  - camera on-demand from the QR tab
- notification chooser execution for phone and link payloads
- App Open Ad gating for launcher-only startup
- inline banner integration in supported app surfaces
- region-aware phone normalization for WhatsApp routing

## Backend Skills

- Supabase migration lifecycle and schema hardening
- Edge Function implementation for pairing, send, ack, and reads
- Firebase push integration for Android delivery
- request guards:
  - anon-key validation
  - request shape and size validation
  - client identity and secret verification
  - throttling and request-event tracking

## Operations Skills

- hosted Supabase CLI workflow
- Firebase project and Android app registration workflow
- Chrome Web Store package generation and config injection
- Android release preparation with required local AdMob config
- cross-module doc synchronization
- cleanup discipline without exposing local credentials
