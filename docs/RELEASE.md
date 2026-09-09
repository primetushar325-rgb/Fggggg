# Release builds & signing

GameSound Pro never stores signing material in the repository. Release signing is driven
entirely by environment variables, which GitHub Actions maps to repository **secrets**.

## 1. Create a keystore (one time, locally)

```bash
keytool -genkeypair -v \
  -keystore gamesoundpro.jks \
  -alias gamesoundpro \
  -keyalg RSA -keysize 4096 -validity 10000
```

Keep `gamesoundpro.jks` and its passwords safe and **out of Git** (`*.jks` / `*.keystore`
are already in `.gitignore`).

## 2. Add repository secrets

On GitHub → *Settings → Secrets and variables → Actions*, add:

| Secret | Value |
| --- | --- |
| `GSP_KEYSTORE_BASE64` | `base64 -w0 gamesoundpro.jks` output (macOS: `base64 -i gamesoundpro.jks`) |
| `GSP_KEYSTORE_PASSWORD` | keystore password |
| `GSP_KEY_ALIAS` | `gamesoundpro` |
| `GSP_KEY_PASSWORD` | key password |

## 3. Build

- **Locally:** point the env vars at your keystore and run Gradle:

  ```bash
  export GSP_KEYSTORE_PATH=/absolute/path/gamesoundpro.jks
  export GSP_KEYSTORE_PASSWORD=...
  export GSP_KEY_ALIAS=gamesoundpro
  export GSP_KEY_PASSWORD=...
  ./gradlew :app:assembleRelease
  # -> app/build/outputs/apk/release/app-release.apk (signed)
  ```

  Without the env vars the release build still succeeds but produces an **unsigned** APK.

- **CI:** push a tag — the `release` job in `.github/workflows/android.yml` decodes the
  keystore from `GSP_KEYSTORE_BASE64`, builds the signed APK and uploads it as an artifact:

  ```bash
  git tag v1.0.0 && git push origin v1.0.0
  ```

## 4. Verify the signature

```bash
$ANDROID_HOME/build-tools/34.0.0/apksigner verify --print-certs app-release.apk
```

## Notes

- `minifyEnabled` + resource shrinking are enabled for release; ProGuard rules live in
  `app/proguard-rules.pro`.
- Debug builds use the application id `com.gamesoundpro.app.debug`, so debug and release
  installs can coexist on one device.
