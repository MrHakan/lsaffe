package com.deckwatch.feature.vessel.common

import com.deckwatch.core.common.ImoNumber

/**
 * Live validation state of the IMO ship identification number field.
 *
 * **The invalid-but-savable rule.** A wrong check digit blocks nothing. On board, the number is
 * copied off the certificate, the bell or the safety plan, and those are occasionally wrong,
 * truncated, or belong to a pre-1996 hull whose number never carried a check digit. Refusing the
 * save would push the officer into recording no IMO number at all, which is strictly worse for a
 * PSC pack. So DeckWatch stores exactly what was typed, marks the vessel with an "unverified IMO"
 * badge everywhere the number is shown, and leaves the correction to the user.
 */
enum class ImoStatus {
    /** Field is empty — an IMO number is optional (§6.1 types it nullable). */
    NOT_ENTERED,

    /** Seven digits with a matching check digit. */
    VALID,

    /** Anything else: wrong length, non-digits, or a check digit that does not match. */
    INVALID,

    ;

    /** True when the value should carry the "unverified IMO" warning badge. */
    val needsWarning: Boolean get() = this == INVALID

    companion object {
        fun of(raw: String?): ImoStatus = when {
            raw.isNullOrBlank() -> NOT_ENTERED
            ImoNumber.isValid(raw) -> VALID
            else -> INVALID
        }
    }
}
