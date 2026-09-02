plugins {
    alias(libs.plugins.deckwatch.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.deckwatch.feature.survivalcraft"

    // The schematic definitions of §7.6 are DATA, not code: they live under
    // src/main/assets/schematics/. The same folder is registered as a Java-resources root so
    // SchematicCatalogue can read them through the class loader — no Context is needed, and the
    // very same bytes are on the JVM unit-test classpath, so the tests exercise the shipped files
    // rather than a copy.
    sourceSets.getByName("main") { resources.srcDir("src/main/assets") }
}

dependencies {
    // Hotspots open the equipment sheet, and the "add" state opens the add sheet — §7.6.
    implementation(project(":feature:feature-equipment"))

    // Schematic, inventory and drill payloads are @Serializable.
    implementation(libs.kotlinx.serialization.json)

    // Panel and hotspot iconography lives outside material-icons-core.
    implementation(libs.androidx.compose.material.icons.extended)
}
