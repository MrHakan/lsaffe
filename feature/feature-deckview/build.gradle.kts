plugins {
    alias(libs.plugins.deckwatch.android.feature)
}

android {
    namespace = "com.deckwatch.feature.deckview"
}

dependencies {
    // The vessel tab hosts the screens these two modules own; it adds the frame around them —
    // vessel selector, overflow, equipment FAB — rather than duplicating any of their UI.
    implementation(project(":feature:feature-vessel"))
    implementation(project(":feature:feature-equipment"))

    // The canvas reads the §18 projection angle and grid-snap settings.
    implementation(project(":core:core-datastore"))

    // The list/plan toggle uses icons outside the material-icons-core set.
    implementation(libs.androidx.compose.material.icons.extended)

    // The canvas view model reads real preferences, so its tests need a real DataStore.
    testImplementation(libs.androidx.datastore.preferences)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
