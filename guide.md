# SupaPhone Development Guide

## Operating Mode

- Pairing-first, no-login architecture
- Reliability-first implementation choices
- Notification flows stay chooser-first
- Full-screen ads stay out of notification-driven flows
- Shareable source of truth is `Foundationvibe/supaphone-v2`
- Local runtime credentials stay outside git

## Core Flows

### Pairing

1. Open the extension pairing view
2. Generate or refresh a 6-digit code, or use the QR
3. Pair on Android using:
   - `Enter Code`
   - `QR Scan`
4. Use Android `Add Device` to pair more browsers later

### Send

1. In the browser, choose a paired device from the context menu
2. Send either:
   - current page or link URL
   - selected phone-like text
3. Backend validates and records the request
4. Android receives the notification
5. Notification body tap opens the chooser flow

### Android Notification Contract

#### Phone payload

- Notification body tap -> chooser
- Chooser options:
  - `Call`
  - `Open in dialer`
  - `WhatsApp`
  - `Share`

#### Link payload

- Notification body tap -> chooser
- Chooser options:
  - `Open link`
  - `Copy link`
  - `Share link`

## Ads Contract

- App Open Ad:
  - launcher-driven open only
  - tied to real startup work
  - never used to delay chooser flows
- Inline banners:
  - allowed in supported in-app screens
  - allowed in chooser screens
- Release rule:
  - never ship Google sample ad IDs in release builds

## Hosted Backend Workflow

Run from `backend/`:

1. `supabase link --project-ref <PROJECT_REF>`
2. `supabase db push --linked`
3. `supabase secrets set ...`
4. `supabase functions deploy <function_name> --use-api --no-verify-jwt`

`--no-verify-jwt` remains intentional for the pairing-first no-login flow.

## QA Sweep Checklist

1. Pairing
- verify 6-digit pairing works
- verify QR pairing works
- verify camera-deny path still allows code pairing

2. Add Device
- from Android Home, tap `Add Device`
- pair another browser and verify return to Home

3. Phone chooser flow
- send a phone payload
- tap the notification body
- verify chooser appears
- verify `Call`, `Open in dialer`, `WhatsApp`, and `Share`

4. Link chooser flow
- send a link payload
- tap the notification body
- verify chooser appears
- verify `Open link`, `Copy link`, and `Share link`

5. Ads
- verify App Open rules apply only on normal app open
- verify chooser/banner surfaces do not block execution
- verify release build uses real AdMob IDs

6. Extension
- refresh paired devices
- rename/remove paired device
- verify popup pairing code and QR
- verify context-menu dispatch

## Documentation Discipline

When behavior changes, update in the same change set:

- `README.md`
- `context.md`
- `guide.md`
- `agents.md`
- `skills.md`
- `SupaPhone.md`
