plugins {
    alias(libs.plugins.deckwatch.android.feature)
}

android {
    namespace = "com.deckwatch.feature.settings"
}

dependencies {
    // The More tab is where the vessel manager and the category manager live (§5, §6.4); it hosts
    // the screens feature-vessel already owns rather than growing its own copies.
    implementation(project(":feature:feature-vessel"))
}
