plugins {
    alias(libs.plugins.deckwatch.android.library)
    alias(libs.plugins.deckwatch.android.hilt)
}

android {
    namespace = "com.deckwatch.core.datastore"
}

dependencies {
    api(project(":core:core-model"))
    implementation(project(":core:core-common"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit4)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}
