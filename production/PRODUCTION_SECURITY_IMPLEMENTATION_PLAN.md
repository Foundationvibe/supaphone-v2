# SupaPhone Production Security Implementation Plan

Status: Confirmed, implementation in progress  
Last updated: 2026-03-08

## Purpose

This document captures the current production security readings for:

1. Android app release to Google Play Store
2. Browser extension release to Chrome Web Store
3. Supabase-backed backend usage in production

This document started as an implementation plan. Execution has now begun, but the GitHub push remains pending explicit approval.

## Scope

Included:

- Android app
- Browser extension
- Supabase Edge Functions
- Store-readiness security and compliance concerns

Excluded:

- QR-related work unless it becomes critical severity

## Executive Summary

The codebase is not showing a critical exploit that should block release immediately.

The highest practical release risk is store and policy compliance mismatch, not a direct source-code secret leak.

Current risk profile:

- High practical risk: inaccurate privacy / data-handling disclosures during Play Store or Chrome Web Store submission
- Medium security risk: public onboarding endpoints can still be spammed or abused
- Medium trust and policy risk: Android package visibility and launch support for unofficial WhatsApp variants
- Low to medium local-risk tradeoff: extension stores browser credentials in local extension storage

## Current Readings

## 1. High Practical Risk: Store Compliance Mismatch

Why this matters:

- The extension sends user-entered content to your backend for paired-device delivery.
- The Android app handles phone numbers, links, push tokens, device identifiers, and package visibility for WhatsApp interoperability.
- If the store listing, privacy policy, or Data Safety answers do not match actual behavior, rejection risk is high even when the code is technically secure.

Relevant code surfaces:

- [background.js](E:/0CODE0/supaphone/browser-extension/background.js)
- [popup.js](E:/0CODE0/supaphone/browser-extension/popup.js)
- [send-payload/index.ts](E:/0CODE0/supaphone/backend/supabase/functions/send-payload/index.ts)
- [register-push-token/index.ts](E:/0CODE0/supaphone/backend/supabase/functions/register-push-token/index.ts)
- [AndroidManifest.xml](E:/0CODE0/supaphone/android-app/app/src/main/AndroidManifest.xml)

Risk interpretation:

- This is the most likely reason for release friction or rejection.
- This is not a code exploit, but it is a release-critical operational risk.

## 2. Medium Security Risk: Public Onboarding Endpoint Abuse

Why this matters:

- The backend intentionally accepts a public anon key for client calls.
- That key is not secret once the app and extension are shipped.
- Onboarding endpoints are therefore public write surfaces and need abuse controls.

Current state:

- [`pairing-code`](E:/0CODE0/supaphone/backend/supabase/functions/pairing-code/index.ts) has a local burst limit, but it is keyed by caller-supplied `browserClientId` and can be bypassed by rotating identities.
- [`register-push-token`](E:/0CODE0/supaphone/backend/supabase/functions/register-push-token/index.ts) currently has no throttling.
- [`requireAnonApiKey`](E:/0CODE0/supaphone/backend/supabase/functions/_shared/runtime.ts) validates the public key correctly, but that alone is not an abuse-control mechanism.

Risk interpretation:

- This is not a direct data-exposure issue.
- This is an abuse, spam, and backend-noise risk.

## 3. Medium Trust / Policy Risk: Unofficial WhatsApp Package Support

Why this matters:

- The Android app explicitly supports unofficial WhatsApp variants.
- Those packages are outside the trust boundary of the official WhatsApp clients.
- This may also be harder to defend during Play review depending on store interpretation and disclosure quality.

Current state:

- [AndroidManifest.xml](E:/0CODE0/supaphone/android-app/app/src/main/AndroidManifest.xml) queries:
  - `com.gbwhatsapp`
  - `com.yowhatsapp`
  - `com.fmwhatsapp`
  - `com.whatsapp.plus`
- [FCMService.kt](E:/0CODE0/supaphone/android-app/app/src/main/java/com/supaphone/app/service/FCMService.kt) includes those packages in the launch order.

Risk interpretation:

- This is not a code execution bug.
- This is a trust-boundary downgrade and a possible policy-review complication.

## 4. Low / Medium Risk: Extension Local Credential Storage

Why this matters:

- The extension stores a browser-specific client credential in local extension storage.
- If the local browser profile is compromised, that credential can be read and reused until rotated.

Current state:

- [background.js](E:/0CODE0/supaphone/browser-extension/background.js) stores:
  - `browserClientId`
  - `browserClientSecret`
- The reset-identity flow is present, which reduces long-term exposure.

Risk interpretation:

- This is a normal extension tradeoff.
- Not a release blocker, but it should be treated as a revocable credential model.

## 5. Low / Medium Risk: Push Identity Is Registered Before Pairing

Why this matters:

- The Android app creates backend presence before the user actually completes pairing.
- This increases background table noise and widens the onboarding write surface.

Current state:

- [MainActivity.kt](E:/0CODE0/supaphone/android-app/app/src/main/java/com/supaphone/app/MainActivity.kt) calls push token sync on launch.
- [register-push-token/index.ts](E:/0CODE0/supaphone/backend/supabase/functions/register-push-token/index.ts) upserts the Android client immediately.

Risk interpretation:

- Not a direct exploit.
- Mostly a hygiene and abuse-surface issue.

## What Is Already In Good Shape

- Strict client-secret verification is already in place on privileged routes.
- Android local storage uses `EncryptedSharedPreferences`.
- Android backup is disabled.
- Lock-screen notifications are redacted.
- Extension permissions remain relatively minimal.
- Runtime secret/config files are excluded from git.

## Implementation Approach

## Track A: Release Compliance Hardening

Priority: Highest

Goal:

- Ensure release metadata and legal disclosures exactly match runtime behavior.

Planned implementation:

1. Create a final data-handling inventory for app + extension:
   - user-entered links
   - user-entered phone numbers
   - device/client identifiers
   - push tokens
   - package visibility / installed-app interoperability
2. Update permanent docs so they describe the actual product behavior.
3. Prepare exact store-declaration language for:
   - Chrome Web Store privacy section
   - Play Store Data Safety
   - privacy policy text
4. Make sure permission justifications match the app flow:
   - camera for QR only
   - call permission for direct call action
   - notifications for payload delivery

Execution output:

- updated permanent markdown docs
- final disclosure checklist for store submission

## Track B: Backend Abuse Controls

Priority: High

Goal:

- Reduce spam and operational abuse on public onboarding endpoints without breaking the current backend-first architecture.

Planned implementation:

1. Add request-fingerprint and IP-aware throttling to [`register-push-token`](E:/0CODE0/supaphone/backend/supabase/functions/register-push-token/index.ts)
2. Strengthen [`pairing-code`](E:/0CODE0/supaphone/backend/supabase/functions/pairing-code/index.ts) so it is not only limited by `browserClientId`
3. Reuse the same abuse-control model already used in [`pairing-complete`](E:/0CODE0/supaphone/backend/supabase/functions/pairing-complete/index.ts)
4. Keep response messages generic enough that they do not help attackers enumerate internals

Implementation preference:

- minimal schema additions if possible
- deterministic throttling windows
- no user-facing behavior change unless abuse thresholds are hit

Expected result:

- lower spam risk
- lower accidental DB growth
- better production resilience

## Track C: Android Trust-Boundary Cleanup

Priority: High for store readiness, Medium for technical security

Goal:

- Reduce Android package-visibility exposure and narrow the trust boundary for Play-distributed builds.

Planned implementation:

Option A: Recommended

- Play build supports only:
  - `com.whatsapp`
  - `com.whatsapp.w4b`
- remove unofficial package queries and launch attempts from the Play release

Option B: Conditional dual-distribution strategy

- Play build uses official WhatsApp packages only
- private/non-Play distribution may keep broader variant support if explicitly desired

Implementation preference:

- build-variant split if you want both behaviors
- otherwise simplify to official packages only

Expected result:

- lower Play review risk
- cleaner trust boundary
- clearer user-data justification

## Track D: Push Registration Timing Cleanup

Priority: Medium

Goal:

- Avoid creating backend Android identities for installs that never complete pairing.

Planned implementation:

1. Move push token registration from app launch to post-pair completion
2. Or keep token locally until the first successful paired session and then register
3. Preserve current user experience:
   - notifications still work once paired
   - no additional permission friction

Expected result:

- less backend noise
- narrower onboarding attack surface
- cleaner production data

## Track E: Extension Credential Handling

Priority: Medium / Low

Goal:

- Keep the current extension model practical while reducing credential lifetime risk.

Planned implementation:

1. Keep browser identity reset in settings
2. Add documentation that the extension credential is device-scoped and revocable
3. Optional future phase:
   - exchange persistent local secret for shorter-lived backend session tokens

Expected result:

- acceptable extension security posture without overcomplicating v1

## Suggested Execution Order

1. Track A: Release compliance hardening
2. Track B: Backend abuse controls
3. Track C: Android trust-boundary cleanup
4. Track D: Push registration timing cleanup
5. Track E: Extension credential refinement

Reason:

- Track A reduces release rejection risk immediately.
- Track B improves backend resilience before scale.
- Track C matters for Play submission safety.
- Tracks D and E improve operational hygiene without needing to block the earlier work.

## Validation Plan

After implementation, validate:

1. Extension:
   - pair flow still works
   - quick send still works
   - reset identity still works
   - stale auth shows clean re-pair messaging
2. Android:
   - first launch permission flow behaves as intended
   - pairing still works
   - push delivery still works
   - lock-screen notification redaction still works
   - WhatsApp action still works for supported packages
3. Backend:
   - throttles apply to onboarding endpoints
   - no regression in pairing or payload send
   - activity logs remain minimal
4. Store-readiness:
   - privacy policy text matches runtime
   - Chrome listing declarations match extension behavior
   - Play Data Safety answers match app behavior

## Manual Decisions Needed From You Before Execution

1. Do you want Play Store Android builds to support only official WhatsApp packages?
2. Do you want a single Android build for all distribution channels, or separate Play / non-Play variants?
3. Do you want push-token registration delayed until after successful pairing?
4. Do you want this plan copied into permanent docs after implementation, or kept as a production-only planning note?

## Recommended Decision Set

Recommended:

1. official WhatsApp packages only for Play builds
2. separate Play and non-Play behavior only if you still need modded package support outside Play
3. delay push-token registration until the device is actually paired
4. keep this file as the production planning record and update `context.md` after execution

## Execution Constraint

Execution was confirmed by the user. Do not push to GitHub until the user explicitly approves.
