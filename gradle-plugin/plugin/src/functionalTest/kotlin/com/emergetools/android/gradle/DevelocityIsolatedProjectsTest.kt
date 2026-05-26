package com.emergetools.android.gradle

import com.autonomousapps.kit.AbstractGradleProject.Companion.PLUGIN_UNDER_TEST_VERSION
import com.autonomousapps.kit.truth.TestKitTruth.Companion.assertThat
import com.emergetools.android.gradle.base.EmergeGradleRunner2
import com.emergetools.android.gradle.projects.SimpleGradleProject
import org.junit.jupiter.api.Test
import java.io.File

class DevelocityIsolatedProjectsTest : EmergePluginTest() {
  @Test
  fun uploadAabUnderIsolatedProjectsWithDevelocity() {
    val project = SimpleGradleProject.createWithVcsInExtension(this, agpVersion = "8.9.0")
    val rootDir = project.gradleProject.rootDir

    // Reuse the repo URLs the kit already wrote into the generated settings.gradle, then
    // overlay Develocity plus the new Emerge settings plugin so we can validate the
    // BuildService wiring under Isolated Projects.
    val settingsFile = File(rootDir, "settings.gradle")
    val repoUrl = Regex("""maven \{ url = '([^']+)' }""")
      .find(settingsFile.readText())
      ?.groupValues
      ?.get(1)
      ?: error("Could not find functional test repo URL in generated settings.gradle")

    // Apply the Emerge settings plugin via classpath (rather than the `plugins {}` block) so
    // there's no version conflict with the project plugin which is also applied via `plugins {}`
    // in :app/build.gradle.
    settingsFile.writeText(
      """
      pluginManagement {
        repositories {
          maven { url = '$repoUrl' }
          gradlePluginPortal()
          mavenCentral()
          google()
        }
      }
      buildscript {
        repositories {
          maven { url = '$repoUrl' }
          google()
          mavenCentral()
        }
        dependencies {
          classpath 'com.emergetools:plugin:$PLUGIN_UNDER_TEST_VERSION'
          classpath 'com.android.tools.build:gradle:8.9.0'
        }
      }
      plugins {
        id 'com.gradle.develocity' version '4.1.1'
      }
      apply plugin: 'com.emergetools.android.settings'
      develocity {
        server = 'https://example.invalid'
        buildScan {
          publishing.onlyIf { false }
          termsOfUseUrl = 'https://example.invalid/tos'
          termsOfUseAgree = 'yes'
        }
      }
      dependencyResolutionManagement {
        repositories {
          maven { url = '$repoUrl' }
          mavenCentral()
          google()
        }
      }
      rootProject.name = 'the-project'
      include ':app'
      """.trimIndent()
    )

    // Now that the Emerge plugin is on the settings buildscript classpath, drop the version
    // from the :app application of `com.emergetools.android` or Gradle will refuse it as a
    // duplicate request.
    val appBuildFile = File(rootDir, "app/build.gradle")
    appBuildFile.writeText(
      appBuildFile.readText().replace(
        Regex("""id 'com\.emergetools\.android' version '[^']+'"""),
        "id 'com.emergetools.android'",
      ),
    )

    val result = EmergeGradleRunner2(rootDir)
      .withArguments(
        ":app:emergeUploadReleaseAab",
        "-x", ":app:lintVitalRelease",
        "-Dorg.gradle.unsafe.isolated-projects=true",
        "-PbaseUrl=$baseUrl",
        "--stacktrace",
      )
      .build()

    assertThat(result).task(":app:emergeUploadReleaseAab").succeeded()
    val emergeViolations = result.output.lines()
      .filter { it.contains("emergetools.android") && it.contains("cannot") }
    check(emergeViolations.isEmpty()) {
      "Found Isolated Projects violations attributed to Emerge code:\n" +
        emergeViolations.joinToString("\n")
    }
  }

}
