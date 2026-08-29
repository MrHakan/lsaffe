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
}
