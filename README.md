# SupaPhone

SupaPhone V2 is a pairing-first browser-to-Android bridge that sends links or phone numbers from desktop to phone with explicit device targeting.

## Status (2026-03-11)

- V2 is now separated into its own repository target: `Foundationvibe/supaphone-v2`.
- Browser extension, Android app, website, and hosted Supabase backend are integrated.
- Pairing remains account-free and uses a 6-digit code or QR.
- Current release focus is launch readiness, store compliance, and final device validation.

## Current Product Behavior

### Pairing

- The extension generates a fresh 6-digit pairing code.
- The extension also renders a local QR inside the popup.
- Android pairing supports:
  - `Enter Code` (default)
  - `QR Scan` (camera permission only when the QR tab is used)
- Android Home includes `Add Device` so additional browsers can be paired without resetting the phone.

### Send Flow

- Extension context menu sends:
  - current page URL
  - link URL
  - selected phone-like text
- Device targeting is explicit.
- Paired devices can be refreshed, renamed, and removed.

### Android Notification and Action Flow

- Notification body tap opens an in-app chooser.
- Phone payload chooser options:
  - `Call`
  - `Open in dialer`
  - `WhatsApp`
  - `Share`
- Link payload chooser options:
  - `Open link`
  - `Copy link`
  - `Share link`
- Direct notification action buttons are no longer part of the V2 notification contract.

### Ads

- App Open Ad is allowed only on normal launcher-driven app open.
- App Open Ad is opportunistic during real startup work only.
- Notification-driven flows stay full-screen-ad free.
- Inline banner ads are used inside stable in-app surfaces.
- Release builds must use real AdMob IDs. Debug builds can fall back to Google test IDs.

## Architecture

- `browser-extension/`: Chrome Manifest V3 extension
- `android-app/`: Kotlin + Jetpack Compose Android app
- `backend/`: Supabase migrations, edge functions, Firebase integration
- `website/`: policy pages and public support surfaces
- `production/`: launch and security planning notes

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
- `rotate-client-secret`

## Security and Reliability Controls

- Strict client identity verification for identity-scoped actions.
- Request validation and bounded input handling across edge functions.
- Request-event throttling on pairing and push-token registration flows.
- Minimal backend logs mode with short retention.
- Pairing completion uses atomic one-time code consumption.
- Android push-token registration happens only after pairing.
- Browser identity reset and 30-day secret-rotation support exist in V2.

## Local Files Required (Not Committed)

- `backend/.env`
- `backend/firebase-service-account.json`
- `android-app/app/google-services.json`
- `android-app/local.properties`
- `browser-extension/config.local.js`

## Release Notes

- Android release builds require real `ADMOB_APP_ID`, `ADMOB_APP_OPEN_AD_UNIT_ID`, and `ADMOB_BANNER_AD_UNIT_ID` in `android-app/local.properties`.
- Chrome Web Store packages should be built from tracked source using `browser-extension/scripts/build-store-package.mjs`.
- The V2 website privacy policy now includes AdMob/UMP disclosure and Chrome extension Limited Use language.
- GitHub Pages is deployed from the `website/` directory through the `Deploy Website To GitHub Pages` workflow.

## Verified Checks (2026-03-11)

- `android-app`: `npm run lint` passed
- `android-app`: `npm run build` passed
- `browser-extension`: `node --check` passed for popup/background/build script
- `browser-extension`: store package build passed
- `android-app`: `:app:assemblePlayDebug` passed under JDK 21
- `android-app`: `:app:bundlePlayRelease` is intentionally blocked until real AdMob release IDs are present in `android-app/local.properties`

## Canonical Project Docs

- `README.md`
- `agents.md`
- `context.md`
- `guide.md`
- `skills.md`
- `SupaPhone.md`
