plugins {
    alias(libs.plugins.deckwatch.android.feature)
}

android {
    namespace = "com.deckwatch.feature.vessel"
}

dependencies {
    // The teaching empty states of DESIGN_OVERHAUL rule 7 need a vessel, a deck stack, a zone and
    // a category icon — none of which are in the material-icons-core set.
    implementation(libs.androidx.compose.material.icons.extended)
}
