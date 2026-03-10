# Manual Setup Checklist

Use this checklist after pulling the latest scaffold.

## 1) Backend `.env`

Create:

- `backend/.env` (copy from `backend/.env.example`)

Fill:

- `SUPABASE_URL`
- `SUPABASE_ANON_KEY`
- `APP_PUBLIC_ANON_KEY` (set this to the same value as `SUPABASE_ANON_KEY`)
- `SUPABASE_SERVICE_ROLE_KEY`
- `SUPABASE_PROJECT_REF`
- `SUPABASE_DB_PASSWORD`
- `FIREBASE_PROJECT_ID`
- `FIREBASE_SERVICE_ACCOUNT_JSON_PATH`
- `FIREBASE_SERVICE_ACCOUNT_JSON_BASE64` (recommended for hosted Supabase Edge)

## 2) Firebase Service Account JSON

Place your downloaded Firebase service account file at:

- `backend/firebase-service-account.json`

and set:

- `FIREBASE_SERVICE_ACCOUNT_JSON_PATH=./firebase-service-account.json` in `backend/.env`

Optional hosted secret (recommended):

- `FIREBASE_SERVICE_ACCOUNT_JSON_BASE64=<base64 of firebase-service-account.json>`

## 3) Android Firebase File

Place:

- `android-app/app/google-services.json`

This file is ignored from git and must stay local.

## 4) Android Local Properties

In:

- `android-app/local.properties`

add:

- `SUPABASE_URL=https://<your-project>.supabase.co`
- `SUPABASE_ANON_KEY=<your-anon-key>`
- `SUPAPHONE_EDGE_BASE_URL=https://<your-project>.functions.supabase.co/functions/v1`

## 5) Browser Extension Backend Config

Copy:

- `browser-extension/config.local.example.js`

to:

- `browser-extension/config.local.js`

Then set:

- `supabaseUrl`
- `supabaseAnonKey`
- `edgeBaseUrl`

This file is ignored from git and should stay local.

## 6) Android FCM Runtime Checklist

FCM runtime is already wired in code now. Validate these files exist and are configured:

- `android-app/app/src/main/java/com/supaphone/app/service/FCMService.kt`
- `android-app/app/src/main/AndroidManifest.xml`
- `android-app/app/google-services.json`

If `google-services.json` is missing, token registration and notifications will not work.

## 7) Android Distribution Flavor

Build and ship the correct Android flavor for the correct channel:

- `play`: use for Google Play Store submission
  - official WhatsApp packages only
- `direct`: use only for direct/private distribution if you intentionally want broader WhatsApp package support

If you are building from Android Studio, select the `play` variant for Play release uploads.

## 8) Optional CI Secrets (Later)

When you automate deployments, add these in GitHub Secrets:

- `SUPABASE_ACCESS_TOKEN`
- `SUPABASE_PROJECT_REF`
- `SUPABASE_DB_PASSWORD`
- `FIREBASE_SERVICE_ACCOUNT_JSON`

## 9) Deploy/Serve Required Edge Functions

Ensure these are available in your Supabase project:

- `pairing-code`
- `pairing-complete`
- `rename-device`
- `remove-paired-device`
- `register-push-token`
- `send-payload`
- `device-ack`
- `paired-devices`
- `recent-pushes`

Deploy with:
- `supabase functions deploy <function_name> --use-api --no-verify-jwt`

## 10) Apply Latest Hardening Migration

Run from `backend/`:

- `supabase db push --linked`

This applies:
- direct table access revocation for `anon/authenticated`
- hourly cleanup schedule for 24h operational data (`activity_logs`, `push_events`, `pairing_attempts`, `request_events`)
- onboarding abuse throttling support for pairing-code and register-push-token
- minimal backend logging mode (error-level activity logs only)

## 11) Post-Hardening Runtime Notes

- After deploying the latest edge functions, client-scoped routes require per-install client secrets.
- Existing extension installs should re-pair once so new browser identity + secret are established.
- Existing Android installs should pair first, then open the app once to register the push token on the paired identity.
- Play Store builds should use the `play` flavor so only official WhatsApp packages are queried/launched.
