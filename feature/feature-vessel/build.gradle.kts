plugins {
    alias(libs.plugins.deckwatch.android.feature)
}

android {
    namespace = "com.deckwatch.feature.vessel"
}

dependencies {
    // Compose screens are verified on the JVM through Robolectric: this project's emulator suite
    // is informational (see ci.yml), so a screen's wiring has to be provable without a device.
    //
    // The tests live in src/testDebug because they need the host activity that
    // compose-ui-test-manifest contributes, and that manifest may only reach the debug variant —
    // shipping a test activity in the release library would be a defect of its own.
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
