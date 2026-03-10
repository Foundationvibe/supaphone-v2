# SupaPhone Development Guide

## Operating Mode

- Pairing-first, no-login architecture.
- Reliability-first implementation choices.
- Keep extension, Android, and backend behavior strictly aligned.

## Core Flows

### Pairing

1. Open extension popup pairing view.
2. Generate/use 6-digit code (QR available in extension and Android).
3. Android pairs via:
  - `Enter Code` (default)
  - `QR Scan` (camera permission requested when QR tab is used)
4. From Android Home, `Add Device` opens pairing again for additional browsers.

### Send

1. In browser context menu, choose target paired device.
2. Send either:
  - current/link URL
  - selected phone-like text
3. Backend validates and dispatches push event.

### Android Notification Contract (Call Payload)

- Notification body tap: open dialer.
- Action `Call`: call intent (permission-aware fallback handled).
- Action `WhatsApp`: opens installed WhatsApp variant/deeplink.

## Android Diagnostics

Use Logcat filter:

- Tag: `SupaPhoneFlow`
- Message prefix: `SPH|`

High-value markers:

- Pairing: `PAIR_SCREEN_OPEN`, `PAIR_CAMERA_RESULT`, `PAIR_CODE_SUBMIT_OK`
- API: `API_CALL_START`, `API_CALL_OK`, `API_CALL_FAIL`
- Push: `PUSH_RECEIVED`, `PUSH_NOTIFY_RENDER`, `PUSH_NOTIFY_SHOWN`
- Actions: `PUSH_NOTIFY_CALL_INTENTS`, `PUSH_NOTIFY_WHATSAPP_INTENT`

## Hosted Supabase Workflow

From `backend/`:

1. `supabase link --project-ref <PROJECT_REF>`
2. `supabase db push --linked`
3. `supabase secrets set --env-file .env`
4. `supabase functions deploy <function_name> --use-api --no-verify-jwt`

`--no-verify-jwt` remains intentional for no-login pairing-only flow.

## QA Sweep Checklist

1. Pairing
- Verify 6-digit pairing works.
- Verify QR pairing works.
- Verify camera deny/block path keeps fallback to 6-digit flow.

2. Add Device
- From Android Home, tap `Add Device`.
- Pair a second browser and verify return to Home.

3. Call + WhatsApp
- Send raw local number (for example, 10-digit national format).
- Verify WhatsApp opens expected international target (region-aware normalization).
- Verify notification body tap opens dialer.
- Verify `Call` action still executes expected call path.

4. Extension actions
- Rename/remove paired device.
- Refresh paired list.
- Validate quick send + context menu dispatch.

## Documentation Discipline

When behavior changes, update in same change set:

- `README.md`
- `context.md`
- `guide.md`
- `agents.md`
- `skills.md`

Temporary planning file policy:

- Keep `TEMP_NEXT_PHASE_IMPLEMENTATION_PLAN.md` local and temporary.
