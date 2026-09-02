plugins {
    alias(libs.plugins.deckwatch.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.deckwatch.feature.report"
}

dependencies {
    // §13.3 Due-list scope consumes DueExportRequest straight from the Due tab.
    implementation(project(":feature:feature-inspection"))

    // §13.6 "Save to Downloads" via the Storage Access Framework.
    implementation(libs.androidx.documentfile)

    implementation(libs.androidx.compose.material.icons.extended)
}
