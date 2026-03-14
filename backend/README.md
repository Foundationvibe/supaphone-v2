# SupaPhone Backend

This folder contains the source-of-truth backend for SupaPhone.

## Scope Included

- Supabase project structure (`supabase/`)
- SQL migrations for pairing, delivery, throttling, and cleanup
- Edge Functions for:
  - pairing code generation
  - pairing completion
  - Android push token registration
  - payload send
  - delivery acknowledgement
  - paired-device listing
  - recent-push listing
  - client secret rotation
- Environment templates with placeholder values only

## Important

- Push delivery requires Firebase credentials and Android `google-services.json`.
- Local runtime files stay out of git.
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

## Local Files You Must Fill

- `backend/.env` (create from `.env.example`)
- `backend/firebase-service-account.json` (not committed)
- `android-app/app/google-services.json` (not committed)
- `android-app/local.properties` values for Supabase keys
- `android-app/local.properties` values for AdMob release IDs
- `browser-extension/config.local.js` (create from `browser-extension/config.local.example.js`)

See `MANUAL_SETUP.md` for the exact list and paths.
