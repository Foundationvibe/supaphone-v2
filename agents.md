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
- launcher-only App Open Ad behavior
- inline banner placement and guardrails
- call, dialer, browser, copy, share, and WhatsApp execution

## Backend Agent

Owns Supabase and Firebase integration:

- schema, migrations, cleanup policies
- Edge Functions for pairing, push registration, send, ack, and reads
- identity verification and secret rotation
- request hardening and throttling

## Maintenance Agent

Owns repo truth and release hygiene:

- keep `README.md`, `guide.md`, `context.md`, `agents.md`, `skills.md`, and `SupaPhone.md` aligned
- keep V2 isolated from the legacy repo path
- keep public docs free of real credentials and local secret values
- keep temporary planning notes clearly marked as supporting docs only

## Coordination State

Last updated: 2026-03-15

- V2 is maintained from the dedicated `supaphone-v2` repository
- chooser-first notification flows are the live contract
- Android release gating includes real AdMob IDs
- public privacy text includes AdMob/UMP and Chrome extension Limited Use language
- local runtime credentials remain machine-local and ignored from git
