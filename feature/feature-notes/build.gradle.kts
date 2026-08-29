plugins {
    alias(libs.plugins.deckwatch.android.feature)
}

android {
    namespace = "com.deckwatch.feature.notes"
}

dependencies {
    // The tab remembers whether its disclaimer strip has been dismissed (§18 settings store).
    implementation(project(":core:core-datastore"))

    // BackHandler for the tab's internal navigation stack.
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material.icons.extended)

    // The chrome view model is exercised against a real Preferences DataStore over a temp file.
    testImplementation(libs.androidx.datastore.preferences)
}
