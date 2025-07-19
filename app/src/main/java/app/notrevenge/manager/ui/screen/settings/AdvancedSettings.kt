package app.notrevenge.manager.ui.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import app.notrevenge.manager.R
import app.notrevenge.manager.domain.manager.Mirror
import app.notrevenge.manager.domain.manager.PreferenceManager
import app.notrevenge.manager.ui.components.settings.SettingsButton
import app.notrevenge.manager.ui.components.settings.SettingsEntry
import app.notrevenge.manager.ui.components.settings.SettingsItemChoice
import app.notrevenge.manager.ui.components.settings.SettingsSwitch
import app.notrevenge.manager.ui.viewmodel.settings.AdvancedSettingsViewModel
import app.notrevenge.manager.utils.DimenUtils
import org.koin.androidx.compose.get

class AdvancedSettings: Screen {

    @Composable
    @OptIn(ExperimentalMaterial3Api::class)
    override fun Content() {
        val ctx = LocalContext.current
        val activity = LocalContext.current as? Activity
        val prefs: PreferenceManager = get()
        val viewModel: AdvancedSettingsViewModel = getScreenModel()
        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

        if (viewModel.showRestartDialog) {
            AlertDialog(
                onDismissRequest = { },
                title = { Text(stringResource(R.string.title_restart_required)) },
                text = { Text(stringResource(R.string.msg_keystore_imported_restart)) },
                confirmButton = {
                    TextButton(onClick = { activity?.finishAffinity() }) {
                        Text(stringResource(R.string.action_close_app))
                    }
                }
            )
        }

        if (viewModel.showRegenerateWarningDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.onRegenerateWarningDialogDismissed() },
                title = { Text(stringResource(R.string.title_warning)) },
                text = { Text(stringResource(R.string.warning_regenerate_keystore)) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.regenerateKeystore()
                        activity?.finishAffinity()
                    }) {
                        Text(stringResource(R.string.action_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.onRegenerateWarningDialogDismissed() }) {
                        Text(stringResource(R.string.action_dismiss_nevermind))
                    }
                }
            )
        }

        val exportLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
            onResult = { uri ->
                uri?.let { viewModel.exportKeystore(it) }
            }
        )

        val importLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent(),
            onResult = { uri ->
                uri?.let { viewModel.importKeystore(it) }
            }
        )

        Scaffold(
            topBar = { TitleBar(scrollBehavior) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
        ) { pv ->
            Column(
                modifier = Modifier
                    .padding(pv)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = DimenUtils.navBarPadding)
            ) {
                SettingsItemChoice(
                    label = stringResource(R.string.settings_check_updates),
                    pref = prefs.updateDuration,
                    labelFactory = {
                        ctx.getString(it.labelRes)
                    },
                    onPrefChange = {
                        prefs.updateDuration = it
                        viewModel.updateCheckerDuration(it)
                    }
                )

                SettingsItemChoice(
                    label = stringResource(R.string.settings_mirror),
                    pref = prefs.mirror,
                    labelFactory = {
                        it.baseUrl.toUri().authority ?: it.baseUrl
                    },
                    onPrefChange = {
                        prefs.mirror = it
                    }
                )

                SettingsItemChoice(
                    label = stringResource(R.string.install_method),
                    pref = prefs.installMethod,
                    labelFactory = {
                        ctx.getString(it.labelRes)
                    },
                    onPrefChange = viewModel::setInstallMethod,
                )

                SettingsSwitch(
                    label = stringResource(R.string.settings_auto_clear_cache),
                    secondaryLabel = stringResource(R.string.settings_auto_clear_cache_description),
                    pref = prefs.autoClearCache,
                    onPrefChange = {
                        prefs.autoClearCache = it
                    }
                )

                SettingsButton(
                    label = stringResource(R.string.action_clear_cache),
                    onClick = {
                        viewModel.clearCache()
                    }
                )

                SettingsEntry(
                    icon = Icons.Outlined.Upload,
                    title = stringResource(R.string.settings_export_keystore_title),
                    summary = stringResource(R.string.settings_export_keystore_summary),
                    onClick = {
                        exportLauncher.launch("notrevenge_keystore.jks")
                    }
                )

                SettingsEntry(
                    icon = Icons.Outlined.Download,
                    title = stringResource(R.string.settings_import_keystore_title),
                    summary = stringResource(R.string.settings_import_keystore_summary),
                    onClick = {
                        importLauncher.launch("application/octet-stream")
                    }
                )

                SettingsEntry(
                    icon = Icons.Outlined.VpnKey,
                    title = stringResource(R.string.settings_regenerate_keystore_title),
                    summary = stringResource(R.string.settings_regenerate_keystore_summary),
                    onClick = {
                        viewModel.showRegenerateWarningDialog = true
                    }
                )
            }
        }
    }

    @Composable
    @OptIn(ExperimentalMaterial3Api::class)
    fun TitleBar(
        scrollBehavior: TopAppBarScrollBehavior
    ) {
        val navigator = LocalNavigator.currentOrThrow

        LargeTopAppBar(
            title = {
                Text(stringResource(R.string.settings_advanced))
            },
            navigationIcon = {
                IconButton(onClick = { navigator.pop() }) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back)
                    )
                }
            },
            scrollBehavior = scrollBehavior
        )
    }

}