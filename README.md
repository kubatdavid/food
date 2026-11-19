# food

Home pantry for Thermomix TM7 cooking: what's in the kitchen, a shopping list and recipes.

| Folder | What |
|---|---|
| `server/` | The website and JSON API served at https://food.force.local (Raspberry Pi: Caddy → Apache/PHP). `catalog.json` is the shared ingredient list with Czech package sizes. |
| `android/` | Android app (Kotlin, Jetpack Compose) for GrapheneOS / Android 17. No Google Play Services. |

## Android app

- Built only by GitHub Actions (`.github/workflows/android.yml`); nothing to install locally.
- Every push to `android/` builds an APK. With the signing secrets set, it is published as a GitHub release
  (`v1.0.<run>`), which Obtainium can track for automatic updates.
- Works on the home network and over WireGuard. It connects to `food.force.local` at the Pi's LAN address and
  trusts only the Caddy local CA for that host (`res/raw/caddy_root.crt`, valid until 2035).

### Signing secrets (repo → Settings → Secrets and variables → Actions)

| Secret | Value |
|---|---|
| `FOOD_KEYSTORE_B64` | base64 of the PKCS#12 release keystore (key alias `food`) |
| `FOOD_KEYSTORE_PASSWORD` | its password |

The keystore itself is never committed. Keep a backup: without it, installed copies can't be updated.
