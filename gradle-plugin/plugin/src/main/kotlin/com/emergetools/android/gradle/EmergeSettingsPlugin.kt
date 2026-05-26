package com.emergetools.android.gradle

import com.emergetools.android.gradle.dv.EmergeBuildScanLinksService
import com.emergetools.android.gradle.dv.registerEmergeBuildScanLinksService
import com.gradle.develocity.agent.gradle.DevelocityConfiguration
import com.gradle.develocity.agent.gradle.scan.BuildScanConfiguration
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.api.provider.Provider

class EmergeSettingsPlugin : Plugin<Settings> {
  override fun apply(settings: Settings) {
    val linksService = settings.gradle.registerEmergeBuildScanLinksService()

    settings.pluginManager.withPlugin("com.gradle.develocity") {
      val develocity = settings.extensions.getByType(DevelocityConfiguration::class.java)
      attachLinks(develocity.buildScan, linksService)
    }
  }

  private fun attachLinks(
    buildScan: BuildScanConfiguration,
    linksService: Provider<EmergeBuildScanLinksService>,
  ) {
    buildScan.buildFinished {
      linksService.get().drainLinks().forEach { link ->
        buildScan.link(link.label, link.url)
      }
    }
  }
}
