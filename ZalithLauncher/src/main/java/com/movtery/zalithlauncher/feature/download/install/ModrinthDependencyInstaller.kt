package com.movtery.zalithlauncher.feature.download.install

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.kdt.mcgui.ProgressLayout
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.event.value.DownloadProgressKeyEvent
import com.movtery.zalithlauncher.feature.download.enums.DependencyType
import com.movtery.zalithlauncher.feature.download.item.ModVersionItem
import com.movtery.zalithlauncher.feature.log.Logging
import com.movtery.zalithlauncher.feature.version.VersionsManager
import com.movtery.zalithlauncher.task.Task
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.modloaders.modpacks.api.ApiHandler
import net.kdt.pojavlaunch.progresskeeper.DownloaderProgressWrapper
import net.kdt.pojavlaunch.utils.DownloadUtils
import org.greenrobot.eventbus.EventBus
import java.io.File

/**
 * Automatically downloads the REQUIRED dependencies of a Modrinth mod
 * (and their required dependencies) into the mods folder.
 */
object ModrinthDependencyInstaller {
    private const val TAG = "DependencyInstaller"

    fun installRequired(api: ApiHandler, version: ModVersionItem, modsDir: File) {
        val required = version.dependencies
            .filter { it.dependencyType == DependencyType.REQUIRED }
            .map { it.projectId }
        if (required.isEmpty()) return

        Task.runTask {
            runCatching { resolveAndDownload(api, version, required, modsDir) }
                .onFailure { Logging.e(TAG, "Dependency install failed: " + Tools.printToString(it)) }
            null
        }.execute()
    }

    private fun resolveAndDownload(
        api: ApiHandler,
        version: ModVersionItem,
        startIds: List<String>,
        modsDir: File
    ) {
        if (!modsDir.exists()) modsDir.mkdirs()

        val info = VersionsManager.getCurrentVersion()?.getVersionInfo()
        val mcVersion = info?.minecraftVersion
        val loaders = LinkedHashSet<String>()
        info?.loaderInfo?.forEach {
            when (it.name.lowercase()) {
                "forge" -> loaders.add("forge")
                "neoforge" -> loaders.add("neoforge")
                "fabric" -> loaders.add("fabric")
                "quilt" -> { loaders.add("quilt"); loaders.add("fabric") }
            }
        }
        if (loaders.isEmpty()) {
            version.modloaders.forEach { if (it.modrinthName.isNotBlank()) loaders.add(it.modrinthName) }
        }

        val visited = HashSet<String>()
        visited.add(version.projectId)
        val queue = ArrayDeque<String>()
        startIds.forEach { queue.add(it) }

        while (queue.isNotEmpty()) {
            val projectId = queue.removeFirst()
            if (!visited.add(projectId)) continue

            val chosen = pickVersion(api, projectId, mcVersion, loaders)
            if (chosen == null) {
                Logging.w(TAG, "No compatible version found for dependency $projectId")
                continue
            }

            val files = chosen.getAsJsonArray("files") ?: continue
            if (files.size() == 0) continue
            var file: JsonObject = files.get(0).asJsonObject
            for (f in files) {
                val o = f.asJsonObject
                if (o.has("primary") && o.get("primary").asBoolean) { file = o; break }
            }

            val fileName = file.get("filename").asString
            val url = file.get("url").asString
            val sha1 = file.getAsJsonObject("hashes")?.get("sha1")?.asString
            val target = File(modsDir, fileName)

            if (!target.exists()) download(url, sha1, target)

            val deps = chosen.getAsJsonArray("dependencies") ?: continue
            for (d in deps) {
                val o = d.asJsonObject
                val type = o.get("dependency_type")?.asString
                val id = o.get("project_id")
                if (type == "required" && id != null && !id.isJsonNull) queue.add(id.asString)
            }
        }
    }

    private fun pickVersion(
        api: ApiHandler,
        projectId: String,
        mcVersion: String?,
        loaders: Set<String>
    ): JsonObject? {
        val query = HashMap<String, Any>()
        if (mcVersion != null) query["game_versions"] = "[\"$mcVersion\"]"
        if (loaders.isNotEmpty()) query["loaders"] = loaders.joinToString(",", "[", "]") { "\"$it\"" }

        val versions = api.get("project/$projectId/version", query, JsonArray::class.java) ?: return null
        if (versions.size() == 0) return null
        for (v in versions) {
            val o = v.asJsonObject
            if (o.get("version_type")?.asString == "release") return o
        }
        return versions.get(0).asJsonObject
    }

    private fun download(url: String, sha1: String?, target: File) {
        val progressKey = target.absolutePath
        EventBus.getDefault().post(DownloadProgressKeyEvent(progressKey, true))
        try {
            val buffer = ByteArray(8192)
            DownloadUtils.ensureSha1<Void?>(target, sha1) {
                Logging.i(TAG, "Download Url: $url")
                DownloadUtils.downloadFileMonitored(
                    url, target, buffer,
                    DownloaderProgressWrapper(R.string.download_install_download_file, progressKey)
                )
                null
            }
        } finally {
            ProgressLayout.clearProgress(progressKey)
            EventBus.getDefault().post(DownloadProgressKeyEvent(progressKey, false))
        }
    }
}
