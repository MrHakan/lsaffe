package com.deckwatch.app

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * DeckWatch runs in English on every device — C8 makes English the app's language, and the
 * Turkish switch of §18 is not built yet.
 *
 * Without this the app follows the phone: a Turkish handset got a half-translated interface, since
 * the bundled regulatory content, the equipment catalogue and the symbol library are English and
 * only the interface strings have Turkish. One language throughout reads better than two mixed.
 *
 * The Turkish resources stay in the tree: when the §18 language setting lands, this is the single
 * place that has to start reading it instead of returning English.
 */
object AppLocale {

    val current: Locale = Locale.ENGLISH

    /**
     * A context whose resources — and whose `Configuration.locales`, which is what the content
     * pickers read to choose `nameEn` over `nameTr` — resolve to [current].
     */
    fun wrap(context: Context): Context {
        Locale.setDefault(current)
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(current)
        configuration.setLayoutDirection(current)
        return context.createConfigurationContext(configuration)
    }
}
