# SupaPhone Development Guide

## Operating Mode

- Pairing-first, no-login architecture.
- Reliability-first implementation choices.
- Notification flows must remain chooser-first.
- Full-screen ads must stay out of notification-driven flows.
- V2 changes ship to the dedicated `supaphone-v2` repo, not the legacy repo.

## Core Flows

### Pairing

1. Open extension popup pairing view.
2. Generate or refresh a 6-digit code, or use the QR.
3. Android pairs via:
   - `Enter Code` (default)
   - `QR Scan` (camera permission requested only from that tab)
4. Android `Add Device` reopens pairing for extra browsers.

### Send

1. In the browser context menu, choose the target paired device.
2. Send either:
   - current page or link URL
   - selected phone-like text
3. Backend validates the request and dispatches the push event.
4. Android receives the notification.
5. Notification body tap opens the chooser flow.

### Android Notification Contract

#### Phone payload

- Notification body tap -> chooser
- Chooser options:
  - `Call`
  - `Open in dialer`
  - `WhatsApp`

#### Link payload

- Notification body tap -> chooser
- Chooser options:
  - `Open link`
  - `Copy link`
  - `Share link`

## Ads Contract

- App Open Ad:
  - normal launcher-driven open only
  - tied to real startup work only
  - never used to delay notification flows
- Inline banners:
  - allowed in stable app screens
  - allowed in chooser screens
- Release rule:
  - never ship Google test/demo ad IDs in release builds

## Hosted Supabase Workflow

From `backend/`:

1. `supabase link --project-ref <PROJECT_REF>`
2. `supabase db push --linked`
3. `supabase secrets set --env-file .env`
4. `supabase functions deploy <function_name> --use-api --no-verify-jwt`

`--no-verify-jwt` remains intentional for the pairing-first no-login flow.

## QA Sweep Checklist

1. Pairing
- Verify 6-digit pairing works.
- Verify QR pairing works.
- Verify camera-deny path still allows code pairing.

2. Add Device
- From Android Home, tap `Add Device`.
- Pair another browser and verify return to Home.

3. Phone chooser flow
- Send a phone payload.
- Tap the notification body.
- Verify chooser appears.
- Verify `Call`, `Open in dialer`, and `WhatsApp` each behave correctly.

4. Link chooser flow
- Send a link payload.
- Tap the notification body.
- Verify chooser appears.
- Verify `Open link`, `Copy link`, and `Share link` each behave correctly.

5. Ads
- Verify launcher-driven App Open Ad rules only apply on normal app open.
- Verify chooser/banner surfaces do not block execution.
- Verify release build uses real AdMob IDs.
- Verify Android debug build on a supported JDK (`21` verified in this workspace).

6. Extension
- Refresh paired devices.
- Rename/remove paired device.
- Verify popup pairing code and QR.
- Verify context-menu dispatch.

## Documentation Discipline

When behavior changes, update in the same change set:

- `README.md`
- `context.md`
- `guide.md`
- `agents.md`
- `skills.md`
- `SupaPhone.md`
