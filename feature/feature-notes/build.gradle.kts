plugins {
    alias(libs.plugins.deckwatch.android.feature)
}

android {
    namespace = "com.deckwatch.feature.notes"
}

dependencies {
    // BackHandler for the tab's internal navigation stack.
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material.icons.extended)
}
