plugins {
    alias(libs.plugins.deckwatch.android.library)
    alias(libs.plugins.deckwatch.android.room)
    alias(libs.plugins.deckwatch.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.deckwatch.core.database"
}

dependencies {
    api(project(":core:core-model"))
    implementation(project(":core:core-common"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.sqlite.ktx)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.room.testing)
}
