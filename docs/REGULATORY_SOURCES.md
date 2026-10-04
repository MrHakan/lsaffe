# REGULATORY SOURCES

Every instrument cited by the bundled content, with the date the summary was
captured and its verification status. **The app never asserts that a specific
revision is current** — flag notices in particular change; each flag card in
the app shows its capture date and carries
`verificationStatus = NEEDS_PERIODIC_REVIEW`.

Capture date for the initial content set: **2026-08-29** (from the project
specification). Items marked UNVERIFIED were not confirmed against the current
instrument text during authoring and render an amber "verify" strip in-app.

| Instrument | Subject | Status |
|---|---|---|
| SOLAS Ch. II-2 | Fire safety; Reg. 14 maintenance plan | UNVERIFIED |
| SOLAS Ch. III | Life-saving appliances; Reg. 19 drills, Reg. 20 maintenance | UNVERIFIED |
| LSA Code | Survival craft, personal appliances, visual signals, launching appliances | UNVERIFIED |
| FSS Code | Fire safety systems | UNVERIFIED |
| MSC.402(96) | Maintenance, thorough examination, operational testing, overhaul and repair of lifeboats, rescue boats, launching appliances and release gear | UNVERIFIED |
| MSC.1/Circ.1432 (as amended) | Maintenance and inspection of fire protection systems and appliances | UNVERIFIED |
| MSC.1/Circ.1318/Rev.1 | Maintenance of fixed CO₂ systems | UNVERIFIED |
| Res. A.951(23) | Improved guidelines for marine portable fire extinguishers | UNVERIFIED |
| Res. A.761(18) | Approval of servicing stations for inflatable liferafts | UNVERIFIED |
| MSC/Circ.1047, MSC/Circ.1114 | Immersion suit testing guidance | UNVERIFIED |
| Res. A.1116(30) | Escape route signs and equipment location markings | UNVERIFIED |
| Res. A.952(23) | Graphical symbols for fire control plans (pre-2019) | UNVERIFIED |
| ISO 24409-1/-2, ISO 17631, ISO 7010, ISO 3864-1 | Symbol design principles and catalogues (geometry/colour reference only — no artwork reproduced) | N/A — see SYMBOL_LICENSING.md |
| RMI Marine Notices 2-011-37, 2-011-14, 2-011-10; Technical Circular 1 | RMI LSA/FFE requirements | NEEDS_PERIODIC_REVIEW |
| Liberia Marine Notices SAF-001, SAF-004, SAF-005, SAF-006, SAF-007, FIR-001, FIR-002 | Liberia LSA/FFE requirements | NEEDS_PERIODIC_REVIEW |
| Panama MMC-281, MMC-258 | Panama LSA/FFE requirements | NEEDS_PERIODIC_REVIEW |
| IACS UR Z17 | Procedural requirements for service suppliers | UNVERIFIED |


## Official IMO review — 4 October 2026 (content bundle 5)

The review used IMO's [current-publication listing](https://www.imo.org/en/publications/Pages/CurrentPublications.aspx), whose downloadable listing is dated **21 September 2026**. Catalogue metadata identifies SOLAS Consolidated Edition 2024 (IH110E), LSA Code 2023 (IF982E), FSS Code 2026 (IC155E) and IAMSAR Volume III 2025 (IL962E). Four publication entries now link those editions to equipment and existing rules. Catalogue verification verifies the edition metadata only; it does not verify the full commercial code or every linked maintenance summary.

| Content | Official public source | Verified scope |
| --- | --- | --- |
| SOLAS January 2026 supplement | [IMO PDF](https://wwwcdn.imo.org/localresources/en/publications/Documents/Supplements/English/QH110E_supplement_January2026.pdf) | Amendment list and entry date; MSC.532(107), II-2/10.11 PFOS prohibition, II-2/1.2.10 first-survey transition for existing ships; pp. 9–10 |
| LSA January 2026 supplement | [IMO PDF](https://wwwcdn.imo.org/localresources/en/publications/Documents/Supplements/English/2QF982E_supplement_January2026_EBK.pdf) | MSC.554(108): hook reset, single-fall exception and lowering-speed provisions; pp. 1–2; entry into force 1 January 2026 |
| Pilot transfer amendments | [MSC.572(110)](https://wwwcdn.imo.org/localresources/en/KnowledgeCentre/IndexofIMOResolutions/MSCResolutions/MSC.572(110).pdf) | Adoption 26 June 2025, acceptance procedure, scheduled 1 January 2028 entry date and installation/transition distinction; explicitly shown as forthcoming |
| Enclosed-space entry recommendations | [MSC.581(110)](https://wwwcdn.imo.org/localresources/en/KnowledgeCentre/IndexofIMOResolutions/MSCResolutions/MSC.581(110).pdf) | Sections 6–7, particularly 7.4 CO₂ testing; corrected the previous summary's conflation of recommendations with statutory instrument carriage requirements |

Only these narrow verified statements and publication metadata receive `VERIFIED`. Older cards retain their review dates and verification state. The outdated generic assertion that launching falls must be turned end-for-end was removed from three summaries; their current consolidated SOLAS/maker requirements remain marked unverified. No new publication changes maintenance task intervals automatically.

Public supplements and resolutions were fetched and read. The app stores their official URLs and short summaries, not copies of commercial IMO books. The supplements page remains the route for future checks: [English IMO supplements](https://www.imo.org/en/publications/pages/english.aspx).

Reference bundle 5 and the seed initializer version 2 refresh installed reference content. Database migration 2 → 3 adds `sourceUrl` and `relatedRefKeys` with empty defaults. User notes, vessel registers, histories and user-defined catalogue rows are preserved. Seed validation rejects dangling context links and non-IMO source URLs.
