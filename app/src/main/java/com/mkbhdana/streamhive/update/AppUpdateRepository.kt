package com.mkbhdana.streamhive.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class AppUpdateInfo(
    val tagName: String,
    val versionName: String,
    val releaseName: String,
    val releaseNotes: String,
    val releaseUrl: String,
    val downloadUrl: String,
    val assetName: String = "",
    val targetAbi: String = "",
    val sizeBytes: Long = 0L
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
                val asset = preferredApkAsset(release)
                Result.success(
                    AppUpdateInfo(
                        tagName = tagName,
                        versionName = latestVersion,
                        releaseName = release.stringOrNull("name").orEmpty().ifBlank { tagName },
                        releaseNotes = release.stringOrNull("body").orEmpty(),
                        releaseUrl = releaseUrl,
                        downloadUrl = asset?.downloadUrl ?: releaseUrl,
                        assetName = asset?.name.orEmpty(),
                        targetAbi = asset?.abi.orEmpty(),
                        sizeBytes = asset?.sizeBytes ?: 0L
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadUpdateApk(
        update: AppUpdateInfo,
        onProgress: (Int) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            suspend fun notifyProgress(progress: Int) {
                withContext(Dispatchers.Main.immediate) {
                    onProgress(progress.coerceIn(0, 100))
                }
            }

            if (update.downloadUrl.isBlank() || !update.downloadUrl.endsWith(".apk", ignoreCase = true)) {
                return@withContext Result.failure(Exception("No APK asset found for this release"))
            }

            val fileName = update.assetName.ifBlank {
                "streamhive-${update.tagName.ifBlank { update.versionName }}-${selectedAbiLabel()}-release.apk"
            }.sanitizeFileName()
            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val targetFile = File(updatesDir, fileName)
            val tempFile = File(updatesDir, "$fileName.download")

            val request = Request.Builder()
                .url(update.downloadUrl)
                .header("User-Agent", "StreamHive/${currentVersionName()}")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("Update download failed: ${response.code}")
                    )
                }

                val body = response.body ?: return@withContext Result.failure(
                    Exception("Update download failed: empty body")
                )
                val totalBytes = body.contentLength()
                var copiedBytes = 0L
                var lastProgress = -1
                body.byteStream().use { input ->
                    tempFile.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            copiedBytes += read
                            if (totalBytes > 0L) {
                                val progress = ((copiedBytes * 100L) / totalBytes).toInt()
                                if (progress != lastProgress) {
                                    lastProgress = progress
                                    notifyProgress(progress)
                                }
                            }
                        }
                    }
                }

                if (targetFile.exists()) targetFile.delete()
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
                validateDownloadedApk(targetFile)
                notifyProgress(100)
                Result.success(targetFile)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun canRequestPackageInstalls(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()
    }

    fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun launchApkInstaller(apkFile: File) {
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, context.packageName)
        }
        context.startActivity(installIntent)
    }

    private fun validateDownloadedApk(apkFile: File) {
        val packageInfo = packageArchiveInfo(apkFile)
            ?: throw IllegalStateException("Downloaded file is not a valid APK")
        if (packageInfo.packageName != context.packageName) {
            throw IllegalStateException("Downloaded APK package does not match this app")
        }

        val downloadedVersion = PackageInfoCompat.getLongVersionCode(packageInfo)
        val installedVersion = currentVersionCode()
        if (downloadedVersion <= installedVersion) {
            throw IllegalStateException("Downloaded APK is not newer than the installed app")
        }
    }

    @Suppress("DEPRECATION")
    private fun packageArchiveInfo(apkFile: File): android.content.pm.PackageInfo? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageArchiveInfo(
                apkFile.path,
                android.content.pm.PackageManager.PackageInfoFlags.of(0L)
            )
        } else {
            context.packageManager.getPackageArchiveInfo(apkFile.path, 0)
        }
    }

    private fun preferredApkAsset(release: JsonObject): ReleaseAsset? {
        val assets = release.getAsJsonArray("assets") ?: return null
        val apkAssets = assets
            .mapNotNull { it.asJsonObject }
            .mapNotNull { asset ->
                val name = asset.stringOrNull("name") ?: return@mapNotNull null
                val downloadUrl = asset.stringOrNull("browser_download_url") ?: return@mapNotNull null
                if (!name.endsWith(".apk", ignoreCase = true)) return@mapNotNull null
                ReleaseAsset(
                    name = name,
                    downloadUrl = downloadUrl,
                    sizeBytes = asset.longOrNull("size") ?: 0L,
                    abi = abiCandidates().firstOrNull { abi -> name.matchesAbi(abi) }.orEmpty()
                )
            }

        val candidates = abiCandidates()
        return candidates.firstNotNullOfOrNull { abi ->
            apkAssets.firstOrNull { it.name.matchesAbi(abi) }
        } ?: apkAssets.firstOrNull { it.name.matchesAbi(UNIVERSAL_ABI) }
            ?: apkAssets.firstOrNull()
    }

    private fun abiCandidates(): List<String> {
        return buildList {
            installedNativeAbi()?.let(::add)
            Build.SUPPORTED_ABIS.forEach(::add)
            add(UNIVERSAL_ABI)
        }
            .map { it.lowercase(Locale.US) }
            .distinct()
    }

    private fun installedNativeAbi(): String? {
        val nativeLibraryDir = context.applicationInfo.nativeLibraryDir
            ?.lowercase(Locale.US)
            .orEmpty()
        return ABI_ALIASES.entries.firstOrNull { (_, aliases) ->
            aliases.any { alias -> alias in nativeLibraryDir }
        }?.key
    }

    private fun selectedAbiLabel(): String {
        return installedNativeAbi() ?: Build.SUPPORTED_ABIS.firstOrNull() ?: UNIVERSAL_ABI
    }

    private fun String.matchesAbi(abi: String): Boolean {
        val lowerName = lowercase(Locale.US)
        return when (abi.lowercase(Locale.US)) {
            "arm64-v8a" -> "arm64-v8a" in lowerName || "-arm64-" in lowerName
            "armeabi-v7a" -> "armeabi-v7a" in lowerName || "arm-v7a" in lowerName || "armv7" in lowerName
            "x86_64" -> "x86_64" in lowerName || "x86-64" in lowerName
            "x86" -> Regex("""(^|[-.])x86($|[-.])""").containsMatchIn(lowerName)
            UNIVERSAL_ABI -> UNIVERSAL_ABI in lowerName
            else -> abi.lowercase(Locale.US) in lowerName
        }
    }

    private fun String.sanitizeFileName(): String {
        return replace(Regex("""[^\w.\-]"""), "_")
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

    @Suppress("DEPRECATION")
    private fun currentVersionCode(): Long {
        return runCatching {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    android.content.pm.PackageManager.PackageInfoFlags.of(0L)
                )
            } else {
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            PackageInfoCompat.getLongVersionCode(packageInfo)
        }.getOrDefault(0L)
    }

    private fun JsonObject.stringOrNull(key: String): String? {
        return get(key)?.takeUnless { it.isJsonNull }?.asString
    }

    private fun JsonObject.longOrNull(key: String): Long? {
        return runCatching {
            get(key)?.takeUnless { it.isJsonNull }?.asLong
        }.getOrNull()
    }

    private data class ReleaseAsset(
        val name: String,
        val downloadUrl: String,
        val sizeBytes: Long,
        val abi: String
    )

    private companion object {
        private const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/mkbhdana/StreamHive/releases/latest"
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        private const val UNIVERSAL_ABI = "universal"

        private val ABI_ALIASES = linkedMapOf(
            "arm64-v8a" to listOf("arm64-v8a", "arm64"),
            "armeabi-v7a" to listOf("armeabi-v7a", "arm"),
            "x86_64" to listOf("x86_64", "x86-64"),
            "x86" to listOf("x86")
        )
    }
}
