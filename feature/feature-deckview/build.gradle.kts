plugins {
    alias(libs.plugins.deckwatch.android.feature)
}

android {
    namespace = "com.deckwatch.feature.deckview"
}

dependencies {
    // The tab hosts the equipment sheet (§7.3/§7.4) and the add-equipment flow (§7.5).
    implementation(project(":feature:feature-equipment"))

    // LIST mode (§7.1C), the vessel selector (§5) and the deck presets (§6.3) are feature-vessel's.
    // The dependency is one-way: feature-vessel must never depend on the renderer.
    implementation(project(":feature:feature-vessel"))

    // The isometric angle and the placement grid are user settings (§18) read straight from prefs.
    implementation(project(":core:core-datastore"))

    // Segmented control, spine and mode icons come from outside the material-icons-core set.
    implementation(libs.androidx.compose.material.icons.extended)

    // The view model reads real settings, so its tests drive a real Preferences DataStore over a
    // temporary file rather than a fake — same approach as core-datastore's own tests.
    testImplementation(libs.androidx.datastore.preferences)

    // VesselTabScreen is verified on the JVM through Robolectric — the emulator suite is
    // informational (see ci.yml), so the tab's wiring has to be provable without a device.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
