# SupaPhone Permission Justification Review

## Scope
This review covers runtime permission purpose, necessity, and user impact for:
- `POST_NOTIFICATIONS`
- `CALL_PHONE`
- `CAMERA`

## Permission Matrix

### 1) Notifications (`POST_NOTIFICATIONS`)
Purpose:
- Deliver real-time push alerts when a paired browser sends a phone number or link.

Why needed:
- Core SupaPhone flow is browser-to-phone handoff. Notifications are the fastest path for immediate action.

What is accessed:
- Notification display capability.
- Firebase Cloud Messaging (FCM) registration token for this device.

What is not used:
- No marketing notifications.
- No ad targeting based on notification data.

User benefit:
- Immediate access to the in-app chooser when a payload notification is opened, followed by explicit phone or link actions based on payload type.

Play Store style justification text:
- "SupaPhone uses notification permission to deliver the phone numbers and links you send from your browser to this device in real time."

Review status:
- Justified and aligned with app objective.

### 2) Call Phone (`CALL_PHONE`)
Purpose:
- Support one-tap direct calling for phone payloads from notifications and recent history.

Why needed:
- Enables direct calling after the user explicitly chooses the `Call` option from the chooser flow.

What is accessed:
- Ability to launch `ACTION_CALL` for user-selected payloads.

What is not used:
- No background auto-calling without user interaction.
- No call log reading or contact list access.

User benefit:
- Faster execution for the app's core "send number to phone and call" workflow once the user opens the chooser and selects `Call`.

Play Store style justification text:
- "SupaPhone requests phone call permission so you can place a direct call from received number actions. If denied, the app opens the dialer instead."

Review status:
- Justified with clear fallback.

### 3) Camera (`CAMERA`)
Purpose:
- Scan QR code for browser-device pairing.

Why needed:
- QR pairing is the quick onboarding path.

What is accessed:
- Camera stream during pairing screen scan session.

What is not used:
- No photo/video capture or storage.
- No background camera usage.

User benefit:
- Faster pairing than manual code entry.

Play Store style justification text:
- "SupaPhone uses camera permission only to scan a pairing QR code. You can pair manually with a 6-digit code if you deny camera access."

Review status:
- Justified with equal-value alternative flow.

## Minimization and Compliance Notes
- Permission requests are tied to functional actions and first-run setup.
- Each sensitive permission has a user-visible fallback or alternative path.
- Current usage is consistent with a utility app and can be disclosed clearly in Play Store Data Safety and in-app policy text.
