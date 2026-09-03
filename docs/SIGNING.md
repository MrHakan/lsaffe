# Signing the release build

## The problem this solves

Android will not install an APK over one signed with a different key. It refuses with *"App not
installed"*, and the only way through is to uninstall first — which takes the register with it.

Until this is set up, that happens on **every** update, because `release.yml` falls back to the
debug keystore when no signing secrets are present, and GitHub's runners are thrown away after each
job — so each build is signed by a different, freshly generated debug key. Two published builds
show it plainly:

| Build | Signer | Certificate SHA-256 |
| --- | --- | --- |
| v0.1.58 | `CN=Android Debug` | `ca422b3a…` |
| v0.1.74 | `CN=Android Debug` | `7494a4b9…` |

Same app, two different keys. Fixing it means signing every build with one key that does not change.

## Setting it up from a phone

No computer needed. The **Bootstrap signing key** workflow generates the key on a runner and writes
it into this repository's secrets itself, so the key is never handled by hand.

1. **Create a token.** GitHub → Settings (your account) → Developer settings → Personal access
   tokens → **Fine-grained tokens** → Generate new token.
   - Repository access: **Only select repositories** → this repository.
   - Permissions → Repository permissions → **Secrets: Read and write**.
   - Expiration: 7 days is plenty; it is used once.
2. **Store it.** This repository → Settings → Secrets and variables → Actions → New repository
   secret, named `BOOTSTRAP_PAT`, with the token as its value.
3. **Run it.** Actions tab → *Bootstrap signing key* → Run workflow.
4. **Clean up.** Delete the `BOOTSTRAP_PAT` secret and revoke the token. It is not needed again.

The workflow refuses to run if `KEYSTORE_BASE64` already exists, so it cannot quietly replace a key
that builds have already been signed with.

## What it creates

| Secret | Holds |
| --- | --- |
| `KEYSTORE_BASE64` | the keystore file, base64-encoded |
| `KEYSTORE_PASSWORD` | a 32-character random password |
| `KEY_ALIAS` | `deckwatch` |
| `KEY_PASSWORD` | the same password as the store |

The two passwords are the same on purpose. PKCS12 — what `keytool` writes by default — does not
support a key password that differs from the store password; given two, it ignores the second one
and the build would then fail to read the key.

## Afterwards

The next build is the **last** one that needs an uninstall first: it is the crossing from the debug
key to this one. Every build after it installs straight over the top.

## The one thing to know about losing it

A GitHub secret cannot be read back out. Once this runs, GitHub holds the only copy of the signing
key. If it is lost, the app cannot be updated in place again — it has to be reinstalled once under a
new key, and everyone carrying it has to do that.

That is an acceptable trade for an app handed round as an APK, and it is why the key is generated
on the runner rather than passed through a chat or an email, where it would outlive the moment it
was needed. It is **not** the right arrangement for a Play Store listing: there, enrol in Play App
Signing and let Google hold the key, with this one as the upload key.

## Setting it up with a computer instead

If you would rather hold the key yourself:

```bash
keytool -genkeypair -v -keystore deckwatch-release.jks -alias deckwatch \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -storepass "$PASS" -keypass "$PASS" -dname "CN=DeckWatch, O=DeckWatch, C=TR"

base64 deckwatch-release.jks | tr -d '\n' > keystore.b64
```

Then add the four secrets above by hand, `keystore.b64` being the value of `KEYSTORE_BASE64`. Keep
the `.jks` backed up somewhere that is not this repository, and delete `keystore.b64` afterwards.

## Checking it worked

The APK on the Releases page should no longer be signed by `CN=Android Debug`, and its certificate
digest should stay the same from one release to the next:

```bash
apksigner verify --print-certs DeckWatch-<version>.apk
```
