# SupaPhone Backend Scaffold

This folder contains the first production-oriented backend scaffold for SupaPhone.

## Scope Included

- Supabase local project structure (`supabase/`)
- Initial SQL schema migration for pairing and push events
- Edge functions for:
  - pairing code generation
  - pairing completion
  - Android push token registration
  - payload send
  - delivery acknowledgement
  - paired-device listing
  - recent-push listing
  - client secret rotation
- Environment templates with explicit manual-key placeholders

## Important

- This scaffold is backend-first and key-driven.
- Push delivery requires Firebase credentials (`FIREBASE_SERVICE_ACCOUNT_JSON_BASE64` or `FIREBASE_SERVICE_ACCOUNT_JSON` or `FIREBASE_SERVICE_ACCOUNT_JSON_PATH`) and Android `google-services.json`.
- Client code should treat this as "backend wired, provider pending" until keys are configured.
- Edge functions now require a valid Supabase public key in request headers (`apikey` + bearer token), validated against `APP_PUBLIC_ANON_KEY`/`SUPABASE_ANON_KEY` (with publishable-key fallback).
- Direct table access for `anon/authenticated` roles is revoked; use edge functions for all app operations.
- High-risk onboarding endpoints now use short-lived request-event throttling with hashed request fingerprints.
- Android push-token registration is accepted only for already-paired phone identities.
- Android release builds require real AdMob IDs in `android-app/local.properties`; debug builds can fall back to Google test IDs.

## Local Commands

Run from `backend/`:

```powershell
supabase start
supabase db reset
supabase functions serve --env-file .env
```

## Files You Must Fill

- `backend/.env` (create from `.env.example`)
- `backend/firebase-service-account.json` (not committed)
- `android-app/app/google-services.json` (not committed)
- `android-app/local.properties` values for Supabase keys
- `android-app/local.properties` values for AdMob release IDs
- `browser-extension/config.local.js` (create from `browser-extension/config.local.example.js`)

See `MANUAL_SETUP.md` for the exact list and paths.
