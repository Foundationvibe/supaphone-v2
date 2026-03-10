# SupaPhone Context

## Product Constraints

- No login or account system.
- Pairing-first onboarding and routing.
- Explicit device targeting for sends.
- Store-safe ad placement rules must be preserved.
- V2 is maintained separately from the legacy `supaphone` repo.

## Runtime Snapshot (2026-03-11)

- Extension, Android app, website, and hosted backend are connected.
- Firebase push delivery is active.
- Pairing is 6-digit code first, with QR as the alternate path.
- Current focus is release readiness and final manual validation.

## Live Behavior

### Extension

- Backend-backed paired-device sync.
- Pairing popup supports 6-digit code and QR.
- Regenerate pairing code is explicit.
- Rename, remove, refresh, and reset-browser-identity flows are available.
- Context menu supports URL and phone payload dispatch.

### Android

- Pairing screen defaults to the 6-digit code tab.
- QR tab requests camera permission only when opened.
- Home screen includes paired browsers, refresh, rename, remove, Add Device, and region selection.
- Notification body tap opens an in-app chooser instead of firing the final action immediately.
- Phone chooser: `Call`, `Open in dialer`, `WhatsApp`.
- Link chooser: `Open link`, `Copy link`, `Share link`.
- App Open Ad is restricted to normal launcher startup only.
- Inline banner ads are allowed in stable app screens and chooser screens.

### Backend

- Edge functions enforce anon-key validation, request shape checks, and identity verification.
- Pairing and push-token registration flows are throttled.
- Browser secret rotation is supported.
- Minimal logs mode is active.
- Pairing codes are short-lived and single-use.

## Operational Priorities

1. Keep browser-to-phone delivery reliable.
2. Keep notification chooser flows fast and predictable.
3. Preserve store compliance across Play and Chrome submission surfaces.
4. Keep canonical docs aligned with current runtime behavior.

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
