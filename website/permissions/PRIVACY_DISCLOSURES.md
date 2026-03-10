# SupaPhone Privacy Disclosures

## 1) Data Sent: Phone/Link Payload

### Plain-language disclosure (in-app)
"When you send from your browser extension, SupaPhone transmits only the selected phone number or link, plus routing metadata needed to deliver it to your paired device."

### Policy-ready disclosure
SupaPhone processes the following operational data to deliver the service:
- Payload content: phone number or URL selected by the user.
- Delivery metadata: source device ID, target device ID, payload type, event ID, and timestamps.
- Pairing metadata: active pair link IDs and pairing code lifecycle data.

Purpose:
- Route the selected payload from browser to paired mobile device.
- Track delivery state (queued, sent, delivered, opened, failed).
- Support recent activity display and troubleshooting.

SupaPhone does not require contacts, SMS, microphone, or location data for this workflow.

## 2) Notification Token Usage

### Plain-language disclosure (in-app)
"SupaPhone stores your device notification token so it can deliver browser-to-phone pushes. The token is used only for service delivery."

### Policy-ready disclosure
SupaPhone registers and stores the Firebase Cloud Messaging (FCM) token for each paired Android client to deliver operational notifications.

Token usage:
- Send service notifications containing user-initiated payloads.
- Maintain push reachability for paired device status.

Token handling:
- Token is stored as service infrastructure data.
- Token is not used for advertising campaigns.
- Token is not sold or shared for third-party marketing.

## 3) Advertising and Consent (AdMob / UMP)

### Plain-language disclosure (in-app / policy)
"SupaPhone shows supported in-app ads in the Android app and may ask for ad privacy choices using Google's consent form where required."

### Policy-ready disclosure
SupaPhone uses the Google Mobile Ads SDK (AdMob) to show app-open and banner ads in supported Android app surfaces and the Google User Messaging Platform (UMP) to request and manage ad privacy choices when required.

Ad-related processing may include:
- consent status and privacy-choice signals
- app or device identifiers used by Google ad services
- IP address and network-derived signals
- device, app, and diagnostics information
- ad request, impression, click, and performance data

Purpose:
- show supported in-app ads
- measure ad delivery and performance
- honor privacy or consent requirements
- support fraud prevention and platform integrity handled by Google ad services

SupaPhone commitments:
- SupaPhone does not use sent links, sent phone numbers, push tokens, or pairing metadata to build ad audiences
- SupaPhone does not sell personal data
- ad-related third-party processing is limited to the Android app surfaces where ads are shown

Store disclosure note:
- Google Play Data Safety and privacy-policy text must disclose ad-related third-party SDK processing and in-app privacy options handling.

## 3B) Chrome Extension Limited Use

### Plain-language disclosure (policy / store)
"The SupaPhone extension uses links, phone numbers, and routing data that you intentionally send only to deliver the selected item to your paired Android device and to keep the service secure and working."

### Policy-ready disclosure
SupaPhone's browser extension handles extension user data only to provide the extension's stated browser-to-phone delivery workflow.

Extension data use boundaries:
- send the selected phone number or URL to the paired Android device
- show paired-device status and recent delivery state
- apply service-security controls, short-term troubleshooting, and operational abuse protection

SupaPhone commitments:
- extension-handled user data is not sold
- extension-handled user data is not used for advertising or unrelated profiling
- extension-handled user data is processed only as needed to provide or secure the SupaPhone workflow

Store disclosure note:
- Public privacy text should explicitly reference Chrome Web Store Limited Use expectations for extension user data.

## 4) Short-Term Logs Retention Policy

### Plain-language disclosure (in-app)
"SupaPhone keeps operational logs for up to 24 hours. You can clear extension logs anytime."

### Policy-ready disclosure
SupaPhone retains operational logs and recent push event records for short-term reliability and debugging.

Retention:
- Standard retention window: up to 24 hours.
- Older entries are pruned by cleanup policy.

User controls:
- Extension-side logs can be cleared by the user.

Data minimization:
- Backend logs are recorded for error events only.
- Only operational fields needed for delivery state and debugging are retained during the retention window.

## 5) Abuse-Protection Telemetry

### Plain-language disclosure (in-app / policy)
"SupaPhone applies short-lived abuse protection on pairing and push-registration requests. This uses derived request fingerprints for rate limiting and does not require account creation."

### Policy-ready disclosure
SupaPhone records short-lived abuse-protection events on selected onboarding endpoints to reduce spam, automated misuse, and accidental request floods.

Abuse-protection data:
- endpoint action name
- client identifier when supplied by the app or extension
- hashed request fingerprint derived from request metadata
- event timestamp

Retention:
- up to 24 hours

Data minimization:
- request fingerprints are stored as derived hashes rather than raw request metadata
- abuse-protection events are used only for operational security and service stability

## 6) Installed App Interoperability (Android)

### Plain-language disclosure (store / policy)
"SupaPhone can open supported WhatsApp apps for user-initiated actions. Google Play builds use official WhatsApp packages only."

### Policy-ready disclosure
SupaPhone checks for compatible WhatsApp apps only to complete a user-initiated WhatsApp action from an incoming call payload notification.

Behavior:
- Google Play-distributed builds support official WhatsApp packages only.
- Direct/private builds may optionally support broader package interoperability if intentionally enabled by the distributor.

Purpose:
- open the selected number in a compatible WhatsApp client chosen by the user action
- provide a fallback path when direct calling is not the desired action

## Suggested UI Copy Blocks

### Permissions screen copy
"SupaPhone needs Notifications for instant delivery, Camera for QR pairing, and Call Phone for one-tap direct calling. You can still pair with code and use dialer fallback if permissions are denied. Ad privacy choices, when required, are managed separately in Settings."

### Settings privacy snippet
"Data processed: sent phone numbers or links, device pairing metadata, notification token for push delivery, short-lived abuse-protection telemetry, and ad-related consent or request data handled through Google ad services. Operational logs are retained for 24 hours."
