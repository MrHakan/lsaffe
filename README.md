# DeckWatch

[![CI](https://github.com/mrhakan/lsaffe/actions/workflows/ci.yml/badge.svg)](https://github.com/mrhakan/lsaffe/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/mrhakan/lsaffe?include_prereleases)](https://github.com/mrhakan/lsaffe/releases)
![minSdk](https://img.shields.io/badge/minSdk-26-blue)
![licence](https://img.shields.io/badge/licence-MIT-green)

**Offline Android app for shipboard LSA / FFE inspection, tracking and
regulatory reference.** Built for the officer responsible for Life-Saving
Appliances and Fire-Fighting Equipment on a merchant vessel.

- **Regulatory notebook** — SOLAS, LSA Code, FSS Code, key IMO circulars,
  flag-state notices (RMI / Liberia / Panama) and class practice, organised to
  answer *how often, by whom, and what evidence* in seconds.
- **Spatial equipment register** — build the vessel deck by deck as a 2.5D
  isometric stack, drop IMO-symbol markers, record condition and type-specific
  attributes, and see what is overdue or needs a shore service provider.
- **One-file HTML export** — a self-contained report that opens in any browser,
  travels over WhatsApp/e-mail, and re-imports on another phone.
- **100% offline**, encrypted local database, no telemetry, no Play Services.

> DeckWatch is a planning and record-keeping aid. It is not a certificate, not
> a substitute for the vessel's approved plans, the manufacturer's manuals,
> class rules or the flag Administration's instructions, and it does not
> discharge any statutory obligation. Regulatory content is a summary captured
> on a stated date and may be superseded. Always verify against the current
> instrument and the vessel's own documentation. The Master's and the Company's
> responsibilities under SOLAS and the ISM Code are unaffected.

## Build

Requirements: JDK 17+, Android SDK (compileSdk 36).

```bash
./gradlew :app:assembleDebug
```

Static analysis and tests:

```bash
./gradlew ktlintCheck detekt test testDebugUnitTest
```

## Release

Every push to `main` republishes the rolling **`main`** pre-release, so the
[Releases page](https://github.com/mrhakan/lsaffe/releases) always carries an
installable build of the current main — `DeckWatch-main.apk`. Pushing a tag
`v*.*.*` (or running the **Release** workflow manually) publishes a versioned
release instead.

Either way the build produces **one signed APK** (universal — every ABI in a
single file, so there is nothing to choose between when sideloading), an AAB
for Play, SHA-256 checksums and generated release notes.

Required repository secrets:

| Secret | How to produce it |
|---|---|
| `KEYSTORE_BASE64` | `keytool -genkeypair -v -keystore release.jks -alias deckwatch -keyalg RSA -keysize 4096 -validity 10000` then `base64 -w0 release.jks` |
| `KEYSTORE_PASSWORD` | as chosen |
| `KEY_ALIAS` | e.g. `deckwatch` |
| `KEY_PASSWORD` | as chosen |

Without the secrets the release workflow still runs and signs with the debug
keystore (marked clearly in the log), so forks can exercise the pipeline.

## Repository layout

Multi-module Gradle build (Kotlin DSL, version catalog, convention plugins in
`build-logic/`). See `MASTER_PROMPT.md` for the full specification,
`docs/DATA_MODEL.md` for the schema, `docs/DECISIONS.md` for decisions and
`docs/BACKLOG.md` for deferred work.

## Licence and content provenance

Code is MIT-licensed (see `LICENSE`). The **regulatory content and the symbol
artwork have their own provenance** and are not covered by the code licence:

- Regulatory summaries: see `docs/REGULATORY_SOURCES.md` — every instrument
  cited, with capture date and verification status.
- Symbol artwork: originally drawn to the published geometry/colour
  specifications, see `docs/SYMBOL_LICENSING.md`. Organisations deploying the
  app in a regulated signage context should obtain an ISO artwork licence.
