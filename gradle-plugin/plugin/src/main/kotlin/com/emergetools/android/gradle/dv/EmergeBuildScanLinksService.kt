package com.emergetools.android.gradle.dv

import org.gradle.api.invocation.Gradle
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.util.concurrent.ConcurrentLinkedQueue

data class EmergeBuildScanLink(
  val label: String,
  val url: String,
)

abstract class EmergeBuildScanLinksService :
  BuildService<BuildServiceParameters.None>,
  AutoCloseable {
  private val links = ConcurrentLinkedQueue<EmergeBuildScanLink>()

  fun link(label: String, url: String) {
    if (label.isNotBlank() && url.isNotBlank()) {
      links.add(EmergeBuildScanLink(label, url))
    }
  }

  fun drainLinks(): List<EmergeBuildScanLink> {
    val result = mutableListOf<EmergeBuildScanLink>()
    while (true) {
      val link = links.poll() ?: break
      result.add(link)
    }
    return result.distinct()
  }

  override fun close() = Unit
}

internal const val EMERGE_BUILD_SCAN_LINKS_SERVICE_NAME = "emergeBuildScanLinks"

fun Gradle.registerEmergeBuildScanLinksService(): Provider<EmergeBuildScanLinksService> =
  sharedServices.registerIfAbsent(
    EMERGE_BUILD_SCAN_LINKS_SERVICE_NAME,
    EmergeBuildScanLinksService::class.java,
  ) {}
