# SupaPhone In-App Behavior Review

## Goal
Verify that denial of sensitive permissions does not silently break user flow and that alternatives exist for core actions.

## Graceful Fallback Review

### Notifications denied
Observed behavior:
- Device can still pair and use app screens.
- Real-time push tray alerts will not appear.

Fallback path:
- User can still open app and check "Recent Pushes" data from backend.
- User can manually act on received payloads from history.

Result:
- Degraded real-time experience, but workflow is still available.

### Call Phone denied
Observed behavior:
- Direct call path (`ACTION_CALL`) is not used.

Fallback path:
- App uses dialer path (`ACTION_DIAL`) so user can still place call manually.

Result:
- Core call workflow remains usable with one extra tap.

### Camera denied
Observed behavior:
- QR scan flow is unavailable.

Fallback path:
- User is guided to "Enter Code Instead" and can pair with 6-digit code.

Result:
- Pairing remains available without camera.

## No Blocked Core Path Without Alternative
Assessment:
- Pairing: QR scan or 6-digit code.
- Calling: direct call or open dialer fallback.
- Link handling: copy link action available.
- Device operations: refresh, rename, remove remain available independent of camera permission.

Conclusion:
- Core app paths have alternatives and are not hard-blocked by a single denied permission.

## Suggested Improvements (Optional)
1. Add a "Permission Status" section in Settings with one-tap deep links to system app settings.
2. Show a persistent but non-intrusive notice when notifications are disabled.
3. Add a first-run explainer card that states fallback behaviors before asking permissions.
