package com.deckwatch.core.testing

import java.util.UUID

/**
 * Default id supplier for the fake repositories. All DeckWatch ids are UUIDv4 strings so that
 * export/import and multi-device merge work without integer collisions — §6.
 *
 * Pass a different supplier to a fake's constructor when a test needs predictable ids.
 */
fun randomId(): String = UUID.randomUUID().toString()

/**
 * A deterministic id supplier: `prefix-1`, `prefix-2`, … Handy when a test asserts on ids or on a
 * golden export payload.
 */
class SequentialIds(private val prefix: String = "id") : () -> String {
    private var next = 0
    override fun invoke(): String = "$prefix-${++next}"
}
