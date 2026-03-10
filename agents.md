# SupaPhone Agents

## Frontend Agent

Owns browser extension behavior and popup UX:
- pairing popup code and QR rendering
- paired-device refresh, rename, remove, reset identity
- context menu send flow and payload classification
- release package generation from tracked source

## Android Agent

Owns native app behavior:
- first-boot permission onboarding
- pairing flows and Add Device
- notification chooser flow for phone and link payloads
- launcher-only app-open ad behavior
- inline banner placement and guardrails
- call, dialer, browser, copy, share, and WhatsApp execution

## Backend Agent

Owns Supabase and Firebase integration:
- schema, migrations, cleanup policies
- edge functions for pairing, push registration, send, ack, and reads
- identity verification and secret rotation
- request hardening and throttling

## Maintenance Agent

Owns repo truth and release hygiene:
- keep `README.md`, `guide.md`, `context.md`, `agents.md`, `skills.md`, and `SupaPhone.md` aligned
- keep V2 isolated from the legacy repo path
- keep store compliance notes aligned with runtime behavior
- keep temporary planning notes clearly marked as supporting docs only

## Coordination State (2026-03-11)

- V2 is now targeted at a dedicated GitHub repo.
- Notification flows are chooser-first.
- Release gating now includes real AdMob IDs for Android release builds.
- Public privacy policy includes AdMob/UMP disclosure and Chrome extension Limited Use language.
