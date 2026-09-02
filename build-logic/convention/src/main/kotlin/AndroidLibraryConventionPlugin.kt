import com.android.build.api.dsl.LibraryExtension
import com.deckwatch.buildlogic.configureKotlinAndroid
import com.deckwatch.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.library")
                apply("org.jetbrains.kotlin.android")
            }

            extensions.configure<LibraryExtension> {
                configureKotlinAndroid(this)
                defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                testOptions {
                    animationsDisabled = true
                    unitTests {
                        isIncludeAndroidResources = true
                        isReturnDefaultValues = true
                    }
                }
            }

            dependencies {
                // The runner named above has to be on the androidTest classpath, or
                // connectedDebugAndroidTest dies with ClassNotFoundException on
                // androidx.test.runner.AndroidJUnitRunner — even in a module with no instrumented
                // tests, because AGP still builds and installs an (empty) test APK for it.
                add("androidTestImplementation", libs.findLibrary("androidx-test-runner").get())
            }
        }
    }
}
