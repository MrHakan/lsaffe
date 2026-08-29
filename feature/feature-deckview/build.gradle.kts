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
}
