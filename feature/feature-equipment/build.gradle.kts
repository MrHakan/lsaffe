plugins {
    alias(libs.plugins.deckwatch.android.feature)
}

android {
    namespace = "com.deckwatch.feature.equipment"
}

dependencies {
    // Chips, task rows and the attribute form use icons outside the material-icons-core set.
    implementation(libs.androidx.compose.material.icons.extended)
}
