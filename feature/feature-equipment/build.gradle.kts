plugins {
    alias(libs.plugins.deckwatch.android.feature)
}

android {
    namespace = "com.deckwatch.feature.equipment"
}

dependencies {
    // EquipmentBottomSheet's onGraded hook exposes ConditionGrade, so core-model is part of this
    // module's public API rather than an implementation detail.
    api(project(":core:core-model"))

    // Condition chips, task rows and the attribute form use icons outside the material-icons-core set.
    implementation(libs.androidx.compose.material.icons.extended)

    // Photo capture launches a camera app through the activity result API — §7.6.
    implementation(libs.androidx.activity.compose)

    // PhotoStore is filesystem code with a Context: provable on the JVM, no emulator needed.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)

    // Verify real sheet gestures and scrolling on the JVM, including the partially open stage.
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
