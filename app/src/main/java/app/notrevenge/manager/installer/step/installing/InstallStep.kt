package app.notrevenge.manager.installer.step.installing

import android.content.Context
import app.notrevenge.manager.R
import app.notrevenge.manager.domain.manager.InstallMethod
import app.notrevenge.manager.domain.manager.PreferenceManager
import app.notrevenge.manager.installer.Installer
import app.notrevenge.manager.installer.session.SessionInstaller
import app.notrevenge.manager.installer.shizuku.ShizukuInstaller
import app.notrevenge.manager.installer.shizuku.ShizukuPermissions
import app.notrevenge.manager.installer.step.Step
import app.notrevenge.manager.installer.step.StepGroup
import app.notrevenge.manager.installer.step.StepRunner
import app.notrevenge.manager.utils.isMiui
import app.notrevenge.manager.utils.showToast
import org.koin.core.component.inject
import java.io.File

/**
 * Installs all the modified splits with the users desired [Installer]
 *
 * @see SessionInstaller
 * @see ShizukuInstaller
 *
 * @param lspatchedDir Where all the patched APKs are
 */
class InstallStep(
    private val lspatchedDir: File
): Step() {

    private val preferences: PreferenceManager by inject()
    private val context: Context by inject()

    override val group = StepGroup.INSTALLING
    override val nameRes = R.string.step_installing

    override suspend fun run(runner: StepRunner) {
        runner.logger.i("Installing apks")
        val files = lspatchedDir.listFiles()
            ?.takeIf { it.isNotEmpty() }
            ?: throw Error("Missing APKs from LSPatch step; failure likely")

        val installMethod = if (preferences.installMethod == InstallMethod.SHIZUKU && !ShizukuPermissions.waitShizukuPermissions()) {
            // Temporarily use DEFAULT if SHIZUKU permissions are not granted
            context.showToast(R.string.msg_shizuku_denied)
            InstallMethod.DEFAULT
        } else {
            preferences.installMethod
        }

        val installer: Installer = when (installMethod) {
            InstallMethod.DEFAULT -> SessionInstaller(context)
            InstallMethod.SHIZUKU -> ShizukuInstaller(context)
        }

        installer.installApks(silent = !isMiui, *files)
    }

}