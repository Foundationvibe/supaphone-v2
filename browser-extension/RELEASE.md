# Browser Extension Release

Build a store-ready extension package from the tracked source tree with:

```powershell
$env:SUPABASE_URL="https://YOUR_PROJECT.supabase.co"
$env:SUPABASE_ANON_KEY="YOUR_SUPABASE_ANON_KEY"
$env:SUPAPHONE_EDGE_BASE_URL="https://YOUR_PROJECT.functions.supabase.co/functions/v1"
node browser-extension/scripts/build-store-package.mjs
```

Output:

- `browser-extension/dist-store/`

Notes:

- `dist-store/` contains a generated `config.runtime.js` for the release package.
- Upload the contents of `dist-store/` to the Chrome Web Store.
- Local development can continue using `browser-extension/config.local.js`.
- Do not upload `browser-extension/` directly to the store; upload `dist-store/` only.
- The generated package removes the need for `config.local.js` inside the store artifact.
- Keep release-time config values local to the build environment. Do not commit real backend values into tracked source.
