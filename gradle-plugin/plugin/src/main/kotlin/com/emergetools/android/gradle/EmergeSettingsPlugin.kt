package com.emergetools.android.gradle

import com.emergetools.android.gradle.dv.EmergeBuildScanLinksService
import com.emergetools.android.gradle.dv.registerEmergeBuildScanLinksService
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.api.provider.Provider

class EmergeSettingsPlugin : Plugin<Settings> {
  override fun apply(settings: Settings) {
    val linksService = settings.gradle.registerEmergeBuildScanLinksService()

    settings.pluginManager.withPlugin("com.gradle.develocity") {
      attachToDevelocity(settings, linksService)
    }

    settings.pluginManager.withPlugin("com.gradle.enterprise") {
      attachToGradleEnterprise(settings, linksService)
    }
  }

  private fun attachToDevelocity(
    settings: Settings,
    linksService: Provider<EmergeBuildScanLinksService>,
  ) {
    val configuration = settings.extensions.findByName("develocity") ?: return
    val buildScan = configuration.javaClass.getMethod("getBuildScan").invoke(configuration)
    attachLinks(buildScan, linksService)
  }

  private fun attachToGradleEnterprise(
    settings: Settings,
    linksService: Provider<EmergeBuildScanLinksService>,
  ) {
    val extension = settings.extensions.findByName("gradleEnterprise") ?: return
    val buildScan = extension.javaClass.getMethod("getBuildScan").invoke(extension)
    attachLinks(buildScan, linksService)
  }

  private fun attachLinks(
    buildScan: Any,
    linksService: Provider<EmergeBuildScanLinksService>,
  ) {
    val buildFinishedMethod = buildScan.javaClass.methods
      .firstOrNull {
        it.name == "buildFinished" &&
          it.parameterCount == 1 &&
          it.parameterTypes[0].isInterface &&
          it.parameterTypes[0].name == "org.gradle.api.Action"
      }
      ?: return
    val actionType = buildFinishedMethod.parameterTypes[0]
    val action = java.lang.reflect.Proxy.newProxyInstance(
      buildScan.javaClass.classLoader,
      arrayOf(actionType),
    ) { _, _, _ ->
      val linkMethod = buildScan.javaClass.getMethod("link", String::class.java, String::class.java)
      linksService.get().drainLinks().forEach { link ->
        linkMethod.invoke(buildScan, link.label, link.url)
      }
      null
    }
    buildFinishedMethod.invoke(buildScan, action)
  }
}
