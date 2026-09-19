/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package love.miao.yun.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import love.miao.yun.MiaoState
import love.miao.yun.util.PrefsBackup

/** The two halves of a settings backup, ready to hang off two rows in either engine. */
class BackupActions(
    val export: () -> Unit,
    val import: () -> Unit,
)

/**
 * Wires the import/export rows to the system file picker.
 *
 * The picker does the file handling, so the app needs no storage permission and the user decides
 * where the file lands — Downloads, a USB stick, wherever. Both engines share this, because a
 * backup that behaved differently in the two UIs would be a bug waiting to be reported.
 */
@Composable
fun rememberBackupActions(onNotify: (String) -> Unit): BackupActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val written = withContext(Dispatchers.IO) {
                runCatching {
                    val json = PrefsBackup.export(context)
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(json.toByteArray())
                    } ?: error("无法写入")
                }.isSuccess
            }
            onNotify(if (written) "已导出配置" else "导出失败")
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val text = context.contentResolver.openInputStream(uri)
                        ?.use { it.readBytes().decodeToString() }
                        ?: error("无法读取这个文件")
                    PrefsBackup.import(context, text)
                }
            }
            outcome
                .onSuccess { count ->
                    refreshFromPreferences(context)
                    onNotify("已导入 $count 项设置")
                }
                .onFailure { error ->
                    onNotify("导入失败：" + (error.message ?: error.toString()))
                }
        }
    }

    return remember(exportLauncher, importLauncher) {
        BackupActions(
            export = { exportLauncher.launch(PrefsBackup.suggestedFileName()) },
            // `*/*` on purpose: plenty of file managers hand a .json back as octet-stream, and the
            // content is validated on the way in anyway.
            import = { importLauncher.launch(arrayOf("*/*")) },
        )
    }
}

/**
 * Pulls the restored values into the process-wide UI state.
 *
 * Import writes preferences, not [MiaoState], so without this the engine, the glass switch and the
 * floating window's palette would keep the values they were read with at startup.
 */
private fun refreshFromPreferences(context: Context) {
    MiaoState.engine = UiEnginePrefs.load(context)
    MiaoState.useBlur = UiEnginePrefs.loadUseBlur(context)
    MiaoState.floatingColorSource = UiEnginePrefs.loadFloatingColor(context)
    MiaoState.developerMode = UiEnginePrefs.loadDeveloperMode(context)
}
