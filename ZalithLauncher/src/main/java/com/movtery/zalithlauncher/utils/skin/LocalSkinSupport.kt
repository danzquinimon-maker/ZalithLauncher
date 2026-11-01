package com.movtery.zalithlauncher.utils.skin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.movtery.zalithlauncher.feature.accounts.AccountType
import com.movtery.zalithlauncher.feature.accounts.AccountsManager
import com.movtery.zalithlauncher.feature.log.Logging
import com.movtery.zalithlauncher.feature.version.Version
import com.movtery.zalithlauncher.task.Task
import com.movtery.zalithlauncher.utils.path.PathManager
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.modloaders.modpacks.api.ApiHandler
import net.kdt.pojavlaunch.utils.DownloadUtils
import net.kdt.pojavlaunch.value.MinecraftAccount
import java.io.File

/**
 * Lets offline (local) accounts have a custom skin inside the real game.
 *
 * - importSkin(): saves a skin PNG picked by the player for an account.
 * - prepare(): before launch, copies that skin into CustomSkinLoader's LocalSkin folder
 *   and downloads the CustomSkinLoader mod (from Modrinth) if the profile has a mod loader.
 */
object LocalSkinSupport {
    private const val TAG = "LocalSkinSupport"
    private const val CSL_PROJECT_ID = "idMHQ4n2" // CustomSkinLoader on Modrinth

    // LocalSkin first (instant, no network wait), Mojang second (other players' skins)
    private const val CSL_CONFIG = """{
  "enable": true,
  "enableTransparentSkin": true,
  "loadlist": [
    {
      "name": "LocalSkin",
      "type": "Legacy",
      "checkPNG": false,
      "skin": "LocalSkin/skins/{USERNAME}.png",
      "model": "auto",
      "cape": "LocalSkin/capes/{USERNAME}.png",
      "elytra": "LocalSkin/elytras/{USERNAME}.png"
    },
    {
      "name": "Mojang",
      "type": "MojangAPI"
    }
  ]
}
"""

    fun importSkin(context: Context, uri: Uri, account: MinecraftAccount): Boolean {
        return runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return false

            // PNG signature check
            if (bytes.size < 8 || bytes[0] != 0x89.toByte() || bytes[1] != 0x50.toByte() ||
                bytes[2] != 0x4E.toByte() || bytes[3] != 0x47.toByte()) return false

            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            val w = opts.outWidth
            val h = opts.outHeight
            if (w < 64 || w % 64 != 0 || (h != w && h != w / 2)) return false

            // Re-encode as a clean 8-bit RGBA PNG (fixes odd PNG formats that render black in game)
            val decodeOpts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inPremultiplied = false
            }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
                ?: return false

            val target = File(PathManager.DIR_USER_SKIN, account.uniqueUUID + ".png")
            target.parentFile?.mkdirs()
            target.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bitmap.recycle()
            true
        }.getOrElse {
            Logging.e(TAG, "Failed to import skin: " + Tools.printToString(it))
            false
        }
    }

    fun prepare(version: Version) {
        runCatching {
            val account = AccountsManager.currentAccount ?: return
            val isOffline = account.accountType == AccountType.LOCAL.type || version.offlineAccountLogin
            if (!isOffline) return

            val skin = File(PathManager.DIR_USER_SKIN, account.uniqueUUID + ".png")
            if (!skin.exists()) return

            val gameDir = version.getGameDir()
            val dest = File(gameDir, "CustomSkinLoader/LocalSkin/skins/${account.username}.png")
            dest.parentFile?.mkdirs()
            skin.copyTo(dest, overwrite = true)

            ensureLocalFirstConfig(File(gameDir, "CustomSkinLoader"))
            ensureSkinMod(version, File(gameDir, "mods"))
        }.onFailure {
            Logging.e(TAG, "Failed to prepare skin: " + Tools.printToString(it))
        }
    }

    private fun ensureLocalFirstConfig(dir: File) {
        val config = File(dir, "CustomSkinLoader.json")
        if (config.exists()) {
            val alreadyGood = runCatching {
                val root = JsonParser().parse(config.readText()).asJsonObject
                val first = root.getAsJsonArray("loadlist")?.get(0)?.asJsonObject?.get("name")?.asString
                first == "LocalSkin"
            }.getOrDefault(false)
            if (alreadyGood) return
            runCatching { config.copyTo(File(dir, "CustomSkinLoader.json.bak"), overwrite = true) }
        }
        dir.mkdirs()
        config.writeText(CSL_CONFIG)
    }

    private fun ensureSkinMod(version: Version, modsDir: File) {
        val info = version.getVersionInfo() ?: return
        val loaders = LinkedHashSet<String>()
        info.loaderInfo?.forEach {
            when (it.name.lowercase()) {
                "forge" -> loaders.add("forge")
                "neoforge" -> loaders.add("neoforge")
                "fabric" -> loaders.add("fabric")
                "quilt" -> { loaders.add("quilt"); loaders.add("fabric") }
            }
        }
        if (loaders.isEmpty()) return // vanilla / OptiFine only: no mod loader to install the mod into

        val already = modsDir.listFiles()?.any {
            it.name.contains("customskinloader", ignoreCase = true)
        } == true
        if (already) return

        val mcVersion = info.minecraftVersion
        Task.runTask {
            runCatching { downloadSkinMod(mcVersion, loaders, modsDir) }
                .onFailure { Logging.e(TAG, "Failed to download CustomSkinLoader: " + Tools.printToString(it)) }
            null
        }.execute()
    }

    private fun downloadSkinMod(mcVersion: String, loaders: Set<String>, modsDir: File) {
        val api = ApiHandler("https://api.modrinth.com/v2")
        val query = HashMap<String, Any>()
        query["game_versions"] = "[\"$mcVersion\"]"
        query["loaders"] = loaders.joinToString(",", "[", "]") { "\"$it\"" }

        val versions = api.get("project/$CSL_PROJECT_ID/version", query, JsonArray::class.java)
        if (versions == null || versions.size() == 0) {
            Logging.w(TAG, "No CustomSkinLoader build found for $mcVersion / $loaders")
            return
        }

        var chosen: JsonObject = versions.get(0).asJsonObject
        for (v in versions) {
            val o = v.asJsonObject
            if (o.get("version_type")?.asString == "release") { chosen = o; break }
        }

        val files = chosen.getAsJsonArray("files") ?: return
        if (files.size() == 0) return
        var file: JsonObject = files.get(0).asJsonObject
        for (f in files) {
            val o = f.asJsonObject
            if (o.has("primary") && o.get("primary").asBoolean) { file = o; break }
        }

        if (!modsDir.exists()) modsDir.mkdirs()
        val target = File(modsDir, file.get("filename").asString)
        if (!target.exists()) {
            DownloadUtils.downloadFile(file.get("url").asString, target)
        }
    }
}
