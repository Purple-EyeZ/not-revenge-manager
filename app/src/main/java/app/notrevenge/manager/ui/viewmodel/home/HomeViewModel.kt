package app.notrevenge.manager.ui.viewmodel.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import app.notrevenge.manager.BuildConfig
import app.notrevenge.manager.R
import app.notrevenge.manager.domain.manager.DownloadManager
import app.notrevenge.manager.domain.manager.InstallManager
import app.notrevenge.manager.domain.manager.InstallMethod
import app.notrevenge.manager.domain.manager.PreferenceManager
import app.notrevenge.manager.domain.repository.RestRepository
import app.notrevenge.manager.installer.Installer
import app.notrevenge.manager.installer.session.SessionInstaller
import app.notrevenge.manager.installer.shizuku.ShizukuInstaller
import app.notrevenge.manager.installer.shizuku.ShizukuPermissions
import app.notrevenge.manager.network.dto.Release
import app.notrevenge.manager.network.utils.CommitsPagingSource
import app.notrevenge.manager.network.utils.dataOrNull
import app.notrevenge.manager.network.utils.ifSuccessful
import app.notrevenge.manager.utils.DiscordVersion
import app.notrevenge.manager.utils.isMiui
import app.notrevenge.manager.utils.showToast
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.launch
import java.io.File

class HomeViewModel(
    private val repo: RestRepository,
    val context: Context,
    val prefs: PreferenceManager,
    val installManager: InstallManager,
    private val downloadManager: DownloadManager
) : ScreenModel {

    private val cacheDir = context.externalCacheDir ?: File(
        Environment.getExternalStorageDirectory(),
        Environment.DIRECTORY_DOWNLOADS
    ).resolve(BuildConfig.MANAGER_NAME).also { it.mkdirs() }

    var discordVersions by mutableStateOf<Map<DiscordVersion.Type, DiscordVersion?>?>(null)
        private set

    var release by mutableStateOf<Release?>(null)
        private set

    private var updateDownloadUrl by mutableStateOf<String?>(null)
    var showUpdateDialog by mutableStateOf(false)
    var isUpdating by mutableStateOf(false)
    val commits = Pager(PagingConfig(pageSize = 30)) { CommitsPagingSource(repo) }.flow.cachedIn(
        screenModelScope
    )

    init {
        getDiscordVersions()
        checkForUpdate()
    }

    fun getDiscordVersions() {
        screenModelScope.launch {
            discordVersions = repo.getLatestDiscordVersions().dataOrNull
            if (prefs.autoClearCache) autoClearCache()
        }
    }

    fun launchMod() {
        installManager.current?.let {
            val intent = context.packageManager.getLaunchIntentForPackage(it.packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun uninstallMod() {
        installManager.uninstall()
    }

    fun launchModInfo() {
        installManager.current?.let {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                data = Uri.parse("package:${it.packageName}")
                context.startActivity(this)
            }
        }
    }

    private fun autoClearCache() {
        val currentVersion =
            DiscordVersion.fromVersionCode(installManager.current?.versionCode.toString()) ?: return
        val latestVersion = when {
            prefs.discordVersion.isBlank() -> discordVersions?.get(prefs.channel)
            else -> DiscordVersion.fromVersionCode(prefs.discordVersion)
        } ?: return

        if (latestVersion > currentVersion) {
            for (file in (context.externalCacheDir ?: context.cacheDir).listFiles()
                ?: emptyArray()) {
                if (file.isDirectory) file.deleteRecursively()
            }
        }
    }

    private fun checkForUpdate() {
        screenModelScope.launch {
            release = repo.getLatestRelease("Purple-EyeZ/not-revenge-manager").dataOrNull
            release?.let {
                updateDownloadUrl = it.assets.firstOrNull { asset -> asset.name.endsWith(".apk") }?.browserDownloadUrl
                showUpdateDialog = it.tagName.removePrefix("v") != BuildConfig.VERSION_NAME
            }
            repo.getLatestRelease("Purple-EyeZ/not-revenge-xposed").ifSuccessful {
                if (prefs.moduleVersion != it.tagName) {
                    prefs.moduleVersion = it.tagName
                    val module = File(cacheDir, "xposed.apk")
                    if (module.exists()) module.delete()
                }
            }
        }
    }

    fun downloadAndInstallUpdate(onProgressUpdate: (Float?) -> Unit) {
        screenModelScope.launch {
            val update = File(cacheDir, "update.apk")
            if (update.exists()) update.delete()
            isUpdating = true
            downloadManager.downloadUpdate(updateDownloadUrl!!, update, onProgressUpdate)
            isUpdating = false

            val installMethod = if (prefs.installMethod == InstallMethod.SHIZUKU && !ShizukuPermissions.waitShizukuPermissions()) {
                // Temporarily use DEFAULT if SHIZUKU permissions are not granted
                    context.showToast(R.string.msg_shizuku_denied)
                    InstallMethod.DEFAULT
                } else {
                    prefs.installMethod
                }

            val installer: Installer = when (installMethod) {
                InstallMethod.DEFAULT -> SessionInstaller(context)
                InstallMethod.SHIZUKU -> ShizukuInstaller(context)
            }

            installer.installApks(silent = !isMiui, update)
        }
    }

}