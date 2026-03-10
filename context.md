# SupaPhone Context

## Product Constraints

- No login/account system in v1.
- Pairing-first onboarding and routing.
- Explicit target-device model for sends.
- Low-ops and low-cost-first infrastructure choices.

## Runtime Snapshot (2026-03-05)

- Extension, Android app, and hosted backend are connected.
- Firebase data push delivery is live.
- System is in stabilization mode (reliability over feature sprawl).

## Live Behavior

### Extension

- Backend-backed paired device sync.
- Pairing view supports both 6-digit code and QR.
- Regenerating pairing code is explicit and loading-aware.
- Rename/remove paired device actions are available.
- Context menu supports URL and phone payload dispatch.
- Local logs are retained and can be cleared in popup flow.

### Android

- Pairing screen defaults to 6-digit code tab.
- QR tab requests camera permission only on demand.
- If camera permission is denied/blocked, user can retry or switch to 6-digit code.
- Home screen:
  - paired browser list + refresh
  - rename/remove actions
  - `Add Device` entry
  - WhatsApp region selector
- Notifications for call payload:
  - body tap -> dialer
  - `Call` action
  - `WhatsApp` action
- WhatsApp launch uses package + deep-link fallback and region-aware number normalization.

### Backend

- Edge functions enforce anon-key validation and request-shape bounds.
- Client secret verification is applied for identity-scoped actions.
- Pairing code cleanup and 24-hour operational cleanup schedules are active.
- Backend activity logs run in minimal mode (error-level events only).
- Pairing completion is rate-limited and consumes codes atomically.
- `send-payload` respects explicit source-target pairing relationship.

## Cleanup and Efficiency Notes

- Unused Android Supabase client path and related dependencies were removed.
- Android network call handling now guarantees connection close (`disconnect()` in `finally`).
- Number normalization keeps dialer compatibility while still producing WhatsApp-safe E.164 routing.

## Operational Priorities

1. Maintain call/WhatsApp delivery consistency across device variants.
2. Preserve pairing reliability when adding/removing multiple browsers.
3. Keep docs and runtime behavior aligned after each change set.

## Documentation Authority

Canonical docs:

- `README.md`
- `agents.md`
- `context.md`
- `guide.md`
- `skills.md`

Temporary/local planning:

- `TEMP_NEXT_PHASE_IMPLEMENTATION_PLAN.md`
