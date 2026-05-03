package com.mkbhdana.streamhive.update

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class AppUpdateInfo(
    val tagName: String,
    val versionName: String,
    val releaseName: String,
    val releaseNotes: String,
    val releaseUrl: String,
    val downloadUrl: String
)

@Singleton
class AppUpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    suspend fun checkForUpdate(): Result<AppUpdateInfo?> = withContext(Dispatchers.IO) {
        try {
            val currentVersion = currentVersionName()
            val request = Request.Builder()
                .url(LATEST_RELEASE_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "StreamHive/$currentVersion")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("Update check failed: ${response.code}")
                    )
                }

                val body = response.body ?: return@withContext Result.failure(
                    Exception("Update check failed: empty body")
                )
                val release = JsonParser.parseReader(body.charStream()).asJsonObject
                val tagName = release.stringOrNull("tag_name") ?: return@withContext Result.success(null)
                val latestVersion = tagName.trimStart('v', 'V')
                if (!isNewerVersion(latestVersion, currentVersion)) {
                    return@withContext Result.success(null)
                }

                val releaseUrl = release.stringOrNull("html_url")
                    ?: "https://github.com/mkbhdana/StreamHive/releases/latest"
                val assetUrl = preferredApkDownloadUrl(release) ?: releaseUrl
                Result.success(
                    AppUpdateInfo(
                        tagName = tagName,
                        versionName = latestVersion,
                        releaseName = release.stringOrNull("name").orEmpty().ifBlank { tagName },
                        releaseNotes = release.stringOrNull("body").orEmpty(),
                        releaseUrl = releaseUrl,
                        downloadUrl = assetUrl
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun preferredApkDownloadUrl(release: JsonObject): String? {
        val assets = release.getAsJsonArray("assets") ?: return null
        val apkAssets = assets
            .mapNotNull { it.asJsonObject }
            .filter { asset ->
                asset.stringOrNull("name")
                    ?.lowercase(Locale.US)
                    ?.endsWith(".apk") == true
            }
        val preferredAsset = apkAssets.firstOrNull { asset ->
            "universal" in asset.stringOrNull("name").orEmpty().lowercase(Locale.US)
        } ?: apkAssets.firstOrNull()

        return preferredAsset?.stringOrNull("browser_download_url")
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val latestParts = versionParts(latest)
        val currentParts = versionParts(current)
        val maxSize = maxOf(latestParts.size, currentParts.size)
        for (index in 0 until maxSize) {
            val latestPart = latestParts.getOrNull(index) ?: 0
            val currentPart = currentParts.getOrNull(index) ?: 0
            if (latestPart != currentPart) return latestPart > currentPart
        }
        return false
    }

    private fun versionParts(version: String): List<Int> {
        return Regex("""\d+""")
            .findAll(version)
            .take(4)
            .map { it.value.toIntOrNull() ?: 0 }
            .toList()
    }

    @Suppress("DEPRECATION")
    private fun currentVersionName(): String {
        return runCatching {
            context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName
                ?: "0"
        }.getOrDefault("0")
    }

    private fun JsonObject.stringOrNull(key: String): String? {
        return get(key)?.takeUnless { it.isJsonNull }?.asString
    }

    private companion object {
        private const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/mkbhdana/StreamHive/releases/latest"
    }
}
