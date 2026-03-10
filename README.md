# SupaPhone

SupaPhone is a pairing-first browser-to-Android bridge that sends links or phone numbers from desktop to phone in one step.

## Status (2026-03-08)

- Browser extension, Android app, and hosted Supabase backend are integrated.
- Firebase data push is active for Android notifications.
- App flow is pairing-first (no login/account system).
- Stabilization and reliability hardening are the current focus.

## What Works

### Pairing

- Browser extension can generate a fresh 6-digit pairing code.
- Extension also renders pairing QR locally inside the popup.
- Android pairing screen supports both:
  - `Enter Code` (default tab)
  - `QR Scan` (camera permission requested only when needed)
- Android home now includes `Add Device`, which reopens pairing flow without unpairing the phone.

### Send Flow

- Extension context menu sends:
  - page/link URL
  - selected phone number or URL-like text
- Device targeting is explicit (no default-device auto-routing).
- Paired devices can be renamed/removed from extension and Android.

### Android Notifications

- Call payload notification:
  - body tap: `Open in Dialer`
  - action 1: `Call`
  - action 2: `WhatsApp`
- Play-distributed Android builds use official WhatsApp packages only (`com.whatsapp`, `com.whatsapp.w4b`).
- Direct/private Android builds can keep broader WhatsApp package fallback when explicitly enabled.
- Number normalization is region-aware (E.164) for WhatsApp reliability.

### Android Home/History

- Home:
  - paired browser list + refresh
  - rename/remove browser actions
  - `Add Device`
  - WhatsApp region selector (used for number normalization)
- History:
  - loads recent pushes from backend
  - link/call actions split by item type

## Architecture

- `browser-extension/`: Chrome Manifest V3 extension (popup + service worker)
- `android-app/`: Kotlin + Jetpack Compose Android app
- `backend/`: Supabase migrations + edge functions + Firebase integration
- `website/`: static web content and policy docs

## Backend Functions

- `pairing-code`
- `pairing-complete`
- `register-push-token`
- `send-payload`
- `device-ack`
- `paired-devices`
- `recent-pushes`
- `rename-device`
- `remove-paired-device`

## Security and Reliability Controls

- Request validation and strict input bounds across edge functions.
- Client identity verification with per-client secret hashing/validation.
- Burst throttling on high-risk endpoints, including request-event tracking for pairing and push-token registration.
- Request-level throttling uses hashed request fingerprints rather than storing raw request metadata.
- Pairing completion now uses request-level throttling + atomic one-time code consumption.
- Android push-token registration happens only after the phone is paired.
- Operational cleanup schedules:
  - pair codes older than 1 hour
  - activity/push logs, pairing attempts, and request-event throttling data older than 24 hours
- Backend activity logs now persist error-level events only (minimal logs mode).
- Android and extension include structured diagnostic logging paths.

## Local Files Required (Not Committed)

- `backend/.env`
- `backend/firebase-service-account.json`
- `android-app/app/google-services.json`
- `android-app/local.properties`
- `browser-extension/config.local.js`

## Development Notes

- Hosted Supabase workflow is supported (linked project path, no local Docker required).
- Extension is loaded unpacked from `browser-extension/`.
- Android app is typically run from Android Studio on device/emulator.
- Android CLI builds now use the committed Gradle wrapper from `android-app/`.
- Use JDK 17 for Android builds. Example:
  - `cd android-app`
  - `.\gradlew.bat :app:assemblePlayDebug`
- Android distributions now use flavors:
  - `play`: official WhatsApp packages only
  - `direct`: broader WhatsApp package interoperability if required outside Play
- Website folder now includes publishable `privacy.html` and `terms.html` pages for store-listing policy links.
- Chrome Web Store packaging should be built from tracked source with:
  - `node browser-extension/scripts/build-store-package.mjs`
  - required env vars: `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `SUPAPHONE_EDGE_BASE_URL`

## Canonical Project Docs

- `README.md`
- `agents.md`
- `context.md`
- `guide.md`
- `skills.md`

Temporary local planning draft:

- `TEMP_NEXT_PHASE_IMPLEMENTATION_PLAN.md`
