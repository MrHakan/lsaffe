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

    // The notification settings read and write the §18 preferences directly.
    implementation(project(":core:core-datastore"))

    // The POST_NOTIFICATIONS request goes through the activity result API.
    implementation(libs.androidx.activity.compose)
}
