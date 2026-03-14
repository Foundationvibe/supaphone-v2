# SupaPhone Edge Functions

Implemented functions:

- `pairing-code` - generate short-lived 6-digit pairing code
- `pairing-complete` - validate code and link browser <-> phone
- `rename-device` - rename a paired target device (browser or android side)
- `remove-paired-device` - revoke an active pair link with a selected device
- `register-push-token` - register/update Android FCM token for a phone client
- `send-payload` - create push event record and queue delivery
- `device-ack` - update delivery status from Android
- `paired-devices` - return paired devices for browser or Android client
- `recent-pushes` - return recent push events for a target client
- `rotate-client-secret` - rotate a registered browser or Android client secret

## Runtime Guards

- All functions validate incoming `apikey`/`Authorization` against `APP_PUBLIC_ANON_KEY` and Supabase-provided public key envs (`SUPABASE_ANON_KEY`, publishable/public fallbacks).
- JSON bodies are size-limited and schema-validated.
- High-risk flows include extra throttling and format checks.
- Pairing and push-registration abuse controls use short-lived `request_events` records with hashed request fingerprints.
- Client-scoped routes (pairing/device/push-history/send/ack paths) require a client secret and verify it using a stored SHA-256 hash in `clients.metadata.auth_secret_hash`.
- Browser and Android clients are expected to keep stable per-install identities; if a local identity rotates, re-pairing is required.
- Android push-token registration is only accepted after a phone identity has been paired.

Serve locally:

```powershell
supabase functions serve --env-file ../.env
```

Deploy example:

```powershell
supabase functions deploy pairing-code --use-api --no-verify-jwt
supabase functions deploy pairing-complete --use-api --no-verify-jwt
supabase functions deploy rename-device --use-api --no-verify-jwt
supabase functions deploy remove-paired-device --use-api --no-verify-jwt
supabase functions deploy register-push-token --use-api --no-verify-jwt
supabase functions deploy send-payload --use-api --no-verify-jwt
supabase functions deploy device-ack --use-api --no-verify-jwt
supabase functions deploy paired-devices --use-api --no-verify-jwt
supabase functions deploy recent-pushes --use-api --no-verify-jwt
supabase functions deploy rotate-client-secret --use-api --no-verify-jwt
```
