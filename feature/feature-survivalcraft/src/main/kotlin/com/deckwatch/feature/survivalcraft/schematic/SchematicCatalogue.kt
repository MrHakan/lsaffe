package com.deckwatch.feature.survivalcraft.schematic

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

/**
 * Loads the bundled schematic definitions and picks the one that fits an equipment type.
 *
 * The files live under `src/main/assets/schematics/`, which the module's build script also
 * registers as a Java-resources root — so they are read through the class loader rather than
 * `AssetManager`. That keeps this class free of `Context` and puts the *shipped* bytes on the JVM
 * unit-test classpath, so a test asserts against the same file the officer sees.
 *
 * Parsing is lazy and happens once; the catalogue is a `@Singleton`.
 */
@Singleton
class SchematicCatalogue(
    private val reader: SchematicResourceReader,
) {

    /** The graph's entry point — the bundled files are ambient content, not a dependency to bind. */
    @Inject
    constructor() : this(ClassLoaderSchematicReader)

    private val json = Json { ignoreUnknownKeys = true }

    private val loaded: List<SchematicDef> by lazy { load() }

    /** Every schematic that ships with the app, in index order. */
    val all: List<SchematicDef> get() = loaded

    /** The generic "children only" schematic used when nothing matches — §7.6 fallback. */
    val fallback: SchematicDef
        get() = loaded.firstOrNull { it.key == GENERIC_KEY } ?: EMPTY_FALLBACK

    /**
     * The schematic drawn for [typeKey]: the first definition that lists it, else [fallback].
     * Selection is by the parent's own type key only — a child never selects the drawing.
     */
    fun forTypeKey(typeKey: String?): SchematicDef =
        loaded.firstOrNull { typeKey != null && typeKey in it.appliesToTypeKeys } ?: fallback

    private fun load(): List<SchematicDef> {
        val indexText = reader.read(INDEX_PATH) ?: return listOf(EMPTY_FALLBACK)
        val index = json.decodeFromString(SchematicIndex.serializer(), indexText)
        return index.files.mapNotNull { path ->
            reader.read(path)?.let { json.decodeFromString(SchematicDef.serializer(), it) }
        }
    }

    private companion object {
        const val INDEX_PATH = "schematics/index.json"
        const val GENERIC_KEY = "GENERIC_COMPONENTS"

        /** Used only when the bundled files cannot be read at all — the screen still renders. */
        val EMPTY_FALLBACK = SchematicDef(
            key = GENERIC_KEY,
            titleEn = "Components",
            titleTr = "Bileşenler",
            aspect = 2.4f,
        )
    }
}

/** Indirection over resource reading so tests can feed synthetic definitions. */
fun interface SchematicResourceReader {
    /** The file's text, or null when it is not bundled. */
    fun read(path: String): String?
}

/** Reads the bundled files through the class loader. */
object ClassLoaderSchematicReader : SchematicResourceReader {
    override fun read(path: String): String? =
        javaClass.classLoader?.getResourceAsStream(path)?.use { it.readBytes().decodeToString() }
}
