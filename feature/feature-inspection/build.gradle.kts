plugins {
    alias(libs.plugins.deckwatch.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.deckwatch.feature.inspection"
}

dependencies {
    // DueExportRequest crosses the module boundary to feature-report as JSON — §13.3.
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.compose.material.icons.extended)
}
