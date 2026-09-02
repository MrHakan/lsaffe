plugins {
    alias(libs.plugins.deckwatch.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.deckwatch.feature.settings"
}

dependencies {
    // §5 — the persistent vessel selector lives in the More tab's top bar.
    implementation(project(":feature:feature-vessel"))

    // §18 — the `.dwbackup` payload IS the §13.2 export payload. The assembler that builds it, the
    // parser that reads it and the merge/apply pipeline that writes it back all live in
    // feature-report; re-implementing any of them here would guarantee the two drift apart.
    implementation(project(":feature:feature-report"))

    // Every §18 setting is read from and written to the settings DataStore directly.
    implementation(project(":core:core-datastore"))

    // §18's automatic weekly backup is a @HiltWorker — see WeeklyBackupWorker.
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Sectioned-list, settings and onboarding iconography sits outside material-icons-core.
    implementation(libs.androidx.compose.material.icons.extended)

    // SAF: the automatic-backup folder (ACTION_OPEN_DOCUMENT_TREE) and the backup file itself.
    implementation(libs.androidx.documentfile)

    // The settings view model drives a real Preferences DataStore over a temporary file rather
    // than a fake, so the write-through tests exercise the real serialisation — the same approach
    // core-datastore and feature-deckview take.
    testImplementation(libs.androidx.datastore.preferences)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
