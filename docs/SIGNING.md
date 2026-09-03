# Signing the release build

## The problem this solves

Android will not install an APK over one signed with a different key. It refuses with *"App not
installed"*, and the only way through is to uninstall first — which takes the register with it.

Until the signing secret is set, that happens on **every** update, because `release.yml` falls back
to the debug keystore when it finds no key, and GitHub's runners are thrown away after each job —
so each build is signed by a different, freshly generated debug key. Two published builds show it:

| Build | Signer | Certificate SHA-256 |
| --- | --- | --- |
| v0.1.58 | `CN=Android Debug` | `ca422b3a…` |
| v0.1.74 | `CN=Android Debug` | `7494a4b9…` |

Same app, two different keys. Fixing it means signing every build with one key that does not change.

## Setting it up

One secret, `KEYSTORE_BASE64`, holding the release keystore base64-encoded:

Repository → Settings → Secrets and variables → Actions → **New repository secret**
- Name: `KEYSTORE_BASE64`
- Secret: the base64 of `deckwatch-release.jks`

That is the whole setup. The password and alias are **not** secrets — they are in `release.yml`:

| | |
| --- | --- |
| Alias | `deckwatch` |
| Password | `DeckWatchRelease2026KeystorePass` |

That looks wrong at first glance and is deliberate. A keystore password guards a keystore *file*.
This file exists in exactly one place — the `KEYSTORE_BASE64` secret — and a secret cannot be read
back out. Anyone who can read the password in this repository cannot reach the file it guards, and
anyone who has somehow obtained the file did not need to read the password here. Splitting it into a
second secret would protect nothing and would double the setup, so the file is the secret and the
password is not.

Both password fields carry the same value because PKCS12 — what `keytool` writes by default — does
not support a key password that differs from the store password. Given two, it ignores the second
one, and the build then fails to read the key.

## Afterwards

The next build is the **last** one that needs an uninstall first: it is the crossing from the debug
key to this one. Every build after it installs straight over the top.

## Losing the key

Once the secret is set, the copy of the keystore that produced it should be kept somewhere safe.
A GitHub secret cannot be read back, so if both are lost the app cannot be updated in place again —
it has to be reinstalled once under a new key, and everyone carrying it has to do that.

For a hand-distributed APK that is a recoverable annoyance. It is **not** the right arrangement for
a Play Store listing: there, enrol in Play App Signing and let Google hold the key, with this one as
the upload key.

## Generating a fresh key

If the key ever has to be replaced — accepting that every installed copy needs one reinstall:

```bash
keytool -genkeypair -v -keystore deckwatch-release.jks -alias deckwatch \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -storepass 'DeckWatchRelease2026KeystorePass' \
  -keypass  'DeckWatchRelease2026KeystorePass' \
  -dname "CN=DeckWatch, O=DeckWatch, C=TR"

base64 deckwatch-release.jks | tr -d '\n'
```

Paste the output as the new `KEYSTORE_BASE64`. If a different password is used, change the two
values in `release.yml` to match.

## Checking it worked

The APK on the Releases page should no longer be signed by `CN=Android Debug`, and its certificate
digest should stay the same from one release to the next:

```bash
apksigner verify --print-certs DeckWatch-<version>.apk
```

The key in use as of this writing:

```
Owner:  CN=DeckWatch, O=DeckWatch, C=TR
Valid:  2026-09-03 → 2054-01-19
SHA256: 31:6B:6E:96:5C:8F:EB:18:1F:4F:95:32:7F:13:F8:A6:6E:2F:C8:07:A0:11:D6:A8:15:4E:56:EA:F0:2B:A0:E6
```
