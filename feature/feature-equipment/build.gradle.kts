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
}
