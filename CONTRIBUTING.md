# Contributing

- **Conventional Commits** (`feat:`, `fix:`, `docs:`, `refactor:`, `test:`,
  `build:`, `ci:`, `chore:`). Semantic versioning; `main` is always releasable.
- Every PR must pass `./gradlew ktlintCheck detekt test testDebugUnitTest
  :app:assembleDebug`.
- No `!!` outside test code. No `GlobalScope`. No blocking calls on the main
  dispatcher.
- All user-facing strings live in `strings.xml` with a `values-tr` counterpart.
- Regulatory content changes must update `docs/REGULATORY_SOURCES.md` and carry
  a `verificationStatus`. **Never invent regulation numbers, paragraph numbers
  or intervals** — a missing card is acceptable, a confidently wrong interval
  is not.
- Symbol artwork must be originally drawn; record provenance in
  `docs/SYMBOL_LICENSING.md`. Do not commit files whose provenance you cannot
  state.
