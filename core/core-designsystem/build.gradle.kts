plugins {
    alias(libs.plugins.deckwatch.android.library)
    alias(libs.plugins.deckwatch.android.compose)
}

android {
    namespace = "com.deckwatch.core.designsystem"
}

dependencies {
    api(project(":core:core-model"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.compose.material.icons.extended)

    testImplementation(libs.junit4)
    testImplementation(libs.truth)

    // The design system now has layout behaviour of its own — spine sizing, plate wrapping — so it
    // is verified on the JVM through Robolectric, like every other Compose surface in the project.
    // The tests live in src/testDebug because compose-ui-test-manifest only reaches the debug variant.
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
