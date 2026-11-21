package architecture.convention

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Общая конфигурация KMP-библиотеки: Android, JVM (Desktop) и iOS.
 *
 * Target'ы и версии задаются здесь, чтобы модули не дублировали конфиг (INV-24).
 */
class KmpLibraryPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        target.plugins.apply("org.jetbrains.kotlin.multiplatform")
        target.plugins.apply("com.android.kotlin.multiplatform.library")

        val namespace = BASE_PACKAGE + target.path.replace(':', '.')

        target.extensions.configure<KotlinMultiplatformExtension> {
            targets.withType<KotlinMultiplatformAndroidLibraryTarget>().configureEach {
                this.namespace = namespace
                compileSdk = AndroidDefaults.COMPILE_SDK
                minSdk = AndroidDefaults.MIN_SDK
                androidResources { enable = true }
            }

            jvm {
                compilerOptions { jvmTarget.set(JvmTarget.JVM_21) }
            }
            iosArm64()
            iosSimulatorArm64()
        }

        val libs = target.libs
        target.dependencies.add(
            "commonMainImplementation",
            libs.findLibrary("kotlinx-coroutines-core").get(),
        )
        target.dependencies.add("commonTestImplementation", libs.findLibrary("kotlin-test").get())
        target.dependencies.add(
            "commonTestImplementation",
            libs.findLibrary("kotlinx-coroutines-test").get(),
        )
    }

    private companion object {
        const val BASE_PACKAGE = "io.github.slavikjunior.syncclip"
    }
}
