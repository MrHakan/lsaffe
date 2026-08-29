package com.deckwatch.core.common

/**
 * IMO ship identification number validation — 7 digits where the last digit is
 * a check digit: sum of (digit * weight) for the first six digits with weights
 * 7,6,5,4,3,2; the check digit is the last digit of that sum.
 */
object ImoNumber {

    fun isValid(raw: String?): Boolean {
        if (raw == null) return false
        val digits = raw.trim().removePrefix("IMO").trim()
        if (digits.length != 7 || digits.any { !it.isDigit() }) return false
        val sum = (0..5).sumOf { i -> (digits[i] - '0') * (7 - i) }
        return sum % 10 == digits[6] - '0'
    }
}
