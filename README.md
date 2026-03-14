# SupaPhone

SupaPhone is a pairing-first browser-to-Android bridge. It sends links or phone numbers from a desktop browser to a paired Android device, then lets the user finish the action on mobile with an explicit chooser.

## Status

Last updated: 2026-03-15

- Public GitHub repo: `Foundationvibe/supaphone-v2`
- Public site: `https://foundationvibe.github.io/supaphone-v2/`
- Pairing is account-free and uses a 6-digit code or QR
- Android app, browser extension, website, Supabase backend, Firebase push, and AdMob wiring are integrated
- Current focus is release signing and store setup

## Current Product Behavior

### Pairing

- The browser extension generates a 6-digit pairing code.
- The extension also renders a QR for the same pairing request.
- Android pairing supports:
  - `Enter Code`
  - `QR Scan`
- Android Home includes `Add Device` so more browsers can be paired without resetting the phone.

### Send Flow

- The extension can send:
  - current page URL
  - link URL
  - selected phone-like text
- Device targeting is explicit.
- Paired devices can be refreshed, renamed, and removed.

### Android Notification and Chooser Flow

- Notification body tap opens an in-app chooser.
- Phone chooser options:
  - `Call`
  - `Open in dialer`
  - `WhatsApp`
  - `Share`
- Link chooser options:
  - `Open link`
  - `Copy link`
  - `Share link`
- Direct notification action buttons are not part of the V2 contract.

### Ads

- App Open Ad is restricted to normal launcher-driven app open.
- App Open Ad is shown only during real startup work.
- Notification-driven flows stay full-screen-ad free.
- Inline banner ads are used in supported in-app surfaces.
- Debug builds default to Google sample ads.
- Release builds require real AdMob IDs.

## Architecture

- `browser-extension/`: Chrome Manifest V3 extension
- `android-app/`: Kotlin + Jetpack Compose Android app
- `backend/`: Supabase migrations, Edge Functions, Firebase push wiring
- `website/`: privacy, support, and terms pages
- `production/`: release and security notes

## Edge Functions

- `pairing-code`
- `pairing-complete`
- `register-push-token`
- `send-payload`
- `device-ack`
- `paired-devices`
- `recent-pushes`
- `rename-device`
- `remove-paired-device`
- `rotate-client-secret`

## Local-Only Runtime Files

These stay on the machine and are not committed:

- `backend/.env`
- `backend/firebase-service-account.json`
- `android-app/app/google-services.json`
- `android-app/local.properties`
- `browser-extension/config.local.js`

## Release Notes

- Android release builds require real `ADMOB_APP_ID`, `ADMOB_APP_OPEN_AD_UNIT_ID`, and `ADMOB_BANNER_AD_UNIT_ID`.
- Chrome Web Store packages should be built from tracked source into `browser-extension/dist-store/`.
- The public privacy policy includes AdMob/UMP disclosure and Chrome extension Limited Use wording.
- GitHub Pages deploys from the `website/` directory.

## Canonical Docs

- `README.md`
- `agents.md`
- `context.md`
- `guide.md`
- `skills.md`
- `SupaPhone.md`
