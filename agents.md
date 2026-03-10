# SupaPhone Agents

## Frontend Agent

Owns extension UI/UX and interaction reliability:
- Pairing views, QR/code rendering, regenerate behavior
- Device list behavior, rename/remove actions
- Context menu send flow and payload classification
- Log view/clear UI and 24-hour retention UX

## Android Agent

Owns native app behavior and call/link execution:
- Permission onboarding (notifications, call, camera)
- Pairing flows (QR scan + code)
- Home and History screens with backend integration
- Push notification rendering and actions
- Call/WhatsApp notification reliability hardening and diagnostics
- Region-aware WhatsApp number normalization and Add-Device pairing flow

## Backend Agent

Owns Supabase + Firebase integration:
- Schema, migrations, indexes, cleanup policies
- Edge functions for pairing, token registration, send, ACK, reads
- Push delivery status transitions and logs
- Hosted deployment and secret-aware runtime behavior
- Request hardening (header auth guard, validation, throttling)

## Maintenance Agent

Owns repository quality and truth synchronization:
- Keep `README.md`, `guide.md`, `context.md`, `agents.md`, `skills.md` aligned with live behavior
- Remove stale assumptions after each functional change
- Keep temporary planning files local-only and explicitly marked non-canonical until migrated
- Retire temporary planning files after migration of useful notes into canonical docs

## Coordination State (2026-03-05)

- Core integration is active and testable.
- Primary risk area is call/WhatsApp action consistency across devices/ROMs.
- Documentation now reflects current implementation state and constraints.
- Next-phase planning draft is tracked in `TEMP_NEXT_PHASE_IMPLEMENTATION_PLAN.md` (temporary/local).
