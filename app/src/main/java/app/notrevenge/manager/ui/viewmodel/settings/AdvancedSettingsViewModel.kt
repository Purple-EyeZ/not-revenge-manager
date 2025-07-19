package app.notrevenge.manager.ui.viewmodel.settings

import android.content.Context
import android.os.Environment
import android.net.Uri
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import app.notrevenge.manager.BuildConfig
import app.notrevenge.manager.R
import app.notrevenge.manager.domain.manager.InstallMethod
import app.notrevenge.manager.domain.manager.PreferenceManager
import app.notrevenge.manager.domain.manager.UpdateCheckerDuration
import app.notrevenge.manager.installer.shizuku.ShizukuPermissions
import app.notrevenge.manager.installer.util.Signer
import app.notrevenge.manager.updatechecker.worker.UpdateWorker
import app.notrevenge.manager.utils.showToast
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AdvancedSettingsViewModel(
    private val context: Context,
    private val prefs: PreferenceManager,
) : ScreenModel {
    private val cacheDir = context.externalCacheDir ?: File(Environment.getExternalStorageDirectory(), Environment.DIRECTORY_DOWNLOADS).resolve(
        BuildConfig.MANAGER_NAME).also { it.mkdirs() }

    fun clearCache() {
        cacheDir.deleteRecursively()
        context.showToast(R.string.msg_cleared_cache)
    }

    fun updateCheckerDuration(updateCheckerDuration: UpdateCheckerDuration) {
        val wm = WorkManager.getInstance(context)
        when (updateCheckerDuration) {
            UpdateCheckerDuration.DISABLED -> wm.cancelUniqueWork("app.notrevenge.manager.UPDATE_CHECK")
            else -> wm.enqueueUniquePeriodicWork(
                "app.notrevenge.manager.UPDATE_CHECK",
                ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
                PeriodicWorkRequestBuilder<UpdateWorker>(
                    updateCheckerDuration.time,
                    updateCheckerDuration.unit
                ).build()
            )
        }
    }

    var showRestartDialog by mutableStateOf(false)
        private set

    var showRegenerateWarningDialog by mutableStateOf(false)

    fun onRestartDialogDismissed() {
        showRestartDialog = false
    }

    fun onRegenerateWarningDialogDismissed() {
        showRegenerateWarningDialog = false
    }

    fun regenerateKeystore() {
        onRegenerateWarningDialogDismissed()
        screenModelScope.launch(Dispatchers.IO) {
            try {
                Signer.regenerate()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setInstallMethod(method: InstallMethod) {
        when (method) {
            InstallMethod.SHIZUKU -> screenModelScope.launch {
                if (ShizukuPermissions.waitShizukuPermissions()) {
                    prefs.installMethod = InstallMethod.SHIZUKU
                } else {
                    context.showToast(R.string.msg_shizuku_denied)
                }
            }

            else -> prefs.installMethod = method
        }
    }

    fun exportKeystore(destinationUri: Uri) {
        performKeystoreOperation {
            val keystoreFile = Signer.keyStore
            if (!keystoreFile.exists()) {
                context.showToast(R.string.msg_keystore_not_found)
                return@performKeystoreOperation
            }
            context.contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
                keystoreFile.inputStream().use { it.copyTo(outputStream) }
            }
            context.showToast(R.string.msg_keystore_exported)
        }
    }

// TODO: check keystore integrity before import
    fun importKeystore(sourceUri: Uri) {
        performKeystoreOperation {
            val keystoreFile = Signer.keyStore
            context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                keystoreFile.outputStream().use { inputStream.copyTo(it) }
            }
            showRestartDialog = true
        }
    }

    private fun performKeystoreOperation(operation: suspend () -> Unit) {
        screenModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) { operation() }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    context.showToast(R.string.msg_keystore_import_failed)
                }
            }
        }
    }

}