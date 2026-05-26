package com.emergetools.android.gradle

import com.autonomousapps.kit.truth.TestKitTruth.Companion.assertThat
import com.emergetools.android.gradle.base.EmergeGradleRunner2
import com.emergetools.android.gradle.projects.SimpleGradleProject
import org.junit.jupiter.api.Test
import java.io.File

class IsolatedProjectsTest : EmergePluginTest() {
  @Test
  fun uploadAabUnderIsolatedProjectsWithDevelocity() {
    val project = SimpleGradleProject.createWithVcsInExtension(this, agpVersion = "8.9.0")
    val rootDir = project.gradleProject.rootDir

    val develocityBlock = """
      plugins {
        id 'com.gradle.develocity' version '3.19.2'
      }
      develocity {
        server = 'https://example.invalid'
        buildScan {
          publishing.onlyIf { false }
          termsOfUseUrl = 'https://example.invalid/tos'
          termsOfUseAgree = 'yes'
        }
      }
    """.trimIndent()

    val settingsFile = File(rootDir, "settings.gradle")
    val original = settingsFile.readText()
    val pmStart = original.indexOf("pluginManagement")
    require(pmStart >= 0) { "Could not locate pluginManagement block" }
    val openBrace = original.indexOf('{', pmStart)
    var depth = 1
    var i = openBrace + 1
    while (i < original.length && depth > 0) {
      when (original[i]) {
        '{' -> depth++
        '}' -> depth--
      }
      i++
    }
    settingsFile.writeText(
      original.substring(0, i) + "\n\n" + develocityBlock + "\n" + original.substring(i)
    )

    val result = EmergeGradleRunner2(rootDir)
      .withArguments(
        ":app:emergeUploadReleaseAab",
        "-x", ":app:lintVitalRelease",
        "-Dorg.gradle.unsafe.isolated-projects=true",
        "-PbaseUrl=$baseUrl",
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
