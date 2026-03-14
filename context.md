# SupaPhone Context

## Product Constraints

- No login or account system
- Pairing-first onboarding and routing
- Explicit device targeting
- Notification flows remain chooser-first
- Full-screen ads stay out of notification-driven flows
- V2 is maintained in the dedicated `supaphone-v2` repository

## Runtime Snapshot

Last updated: 2026-03-15

- Extension, Android app, website, and hosted backend are connected
- Firebase push delivery is active on the current migrated stack
- Pairing is 6-digit code first, with QR as the alternate path
- Local runtime config is intentionally kept out of git
- Current focus is release readiness and store submission setup

## Live Behavior

### Extension

- Backend-backed paired-device sync
- Pairing popup supports 6-digit code and QR
- Explicit regenerate pairing code flow
- Rename, remove, refresh, and reset-browser-identity flows
- Context menu support for URL and phone payload dispatch

### Android

- Pairing screen supports code and QR
- QR tab requests camera permission only when opened
- Home includes paired browsers, refresh, rename, remove, Add Device, and region selection
- Notification body tap opens an in-app chooser
- Phone chooser: `Call`, `Open in dialer`, `WhatsApp`, `Share`
- Link chooser: `Open link`, `Copy link`, `Share link`
- App Open Ad is restricted to normal launcher startup only
- Inline banner ads are used in supported app screens

### Backend

- Edge Functions enforce anon-key validation, request validation, and identity verification
- Pairing and push-token registration flows are throttled
- Browser secret rotation is supported on a 30-day interval
- Pairing codes are short-lived and single-use
- Hosted backend can be rebuilt from migrations and function source

## Operational Priorities

1. Keep browser-to-phone delivery reliable
2. Keep chooser flows fast and predictable
3. Keep store disclosures aligned with runtime behavior
4. Keep public docs and release docs aligned with the current repo state

## Documentation Authority

Canonical docs:

- `README.md`
- `agents.md`
- `context.md`
- `guide.md`
- `skills.md`
- `SupaPhone.md`

Supporting docs:

- `backend/README.md`
- `backend/MANUAL_SETUP.md`
- `browser-extension/RELEASE.md`
- `website/permissions/PRIVACY_DISCLOSURES.md`
- `production/PRODUCTION_SECURITY_IMPLEMENTATION_PLAN.md`
