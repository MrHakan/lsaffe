plugins {
    alias(libs.plugins.deckwatch.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":core:core-model"))
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
