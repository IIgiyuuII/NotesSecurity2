package edu.androidsec.notes.reminder

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import edu.androidsec.notes.R

private const val NOTIFICATION_PERMISSION = Manifest.permission.POST_NOTIFICATIONS

/**
 * Разрешение на уведомления (ipc-2). Возвращает лямбду «запросить разрешение и, если оно есть,
 * выполнить [onGranted]»; её вызывает кнопка «Напомнить», то есть запрос идёт в момент
 * потребности, а не при запуске.
 *
 * Сценарий:
 * 1. Разрешение уже есть (или API < 33, где его не существует) — сразу [onGranted].
 * 2. Нет — сначала диалог с объяснением, зачем оно нужно, затем системный запрос.
 * 3. Первый отказ — сообщение, приложение работает дальше (заметка сохранена, просто без
 *    напоминания).
 * 4. Повторный отказ: система больше не показывает диалог (`shouldShowRequestPermissionRationale`
 *    после отказа уже false) — предлагаем открыть настройки уведомлений приложения.
 */
@Composable
fun rememberNotificationPermission(onGranted: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val currentOnGranted by rememberUpdatedState(onGranted)

    var showRationale by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    // Система уже отказалась показывать диалог: спрашивать бессмысленно, ведём в настройки.
    var blocked by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        when {
            granted -> currentOnGranted()
            activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, NOTIFICATION_PERMISSION) -> {
                // Отказ, после которого система больше не спросит.
                blocked = true
                showSettings = true
            }
            else -> Toast.makeText(context, R.string.permission_denied_toast, Toast.LENGTH_LONG).show()
        }
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            title = { Text(stringResource(R.string.permission_rationale_title)) },
            text = { Text(stringResource(R.string.permission_rationale_text)) },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    launcher.launch(NOTIFICATION_PERMISSION)
                }) { Text(stringResource(R.string.permission_rationale_continue)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRationale = false
                    Toast.makeText(context, R.string.permission_denied_toast, Toast.LENGTH_LONG).show()
                }) { Text(stringResource(R.string.permission_rationale_cancel)) }
            },
        )
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text(stringResource(R.string.permission_settings_title)) },
            text = { Text(stringResource(R.string.permission_settings_text)) },
            confirmButton = {
                TextButton(onClick = {
                    showSettings = false
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            .also { if (context !is Activity) it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
                    )
                }) { Text(stringResource(R.string.permission_settings_open)) }
            },
            dismissButton = {
                TextButton(onClick = { showSettings = false }) {
                    Text(stringResource(R.string.permission_settings_cancel))
                }
            },
        )
    }

    return {
        when {
            context.hasNotificationPermission() -> currentOnGranted()
            blocked -> showSettings = true
            else -> showRationale = true
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Есть ли у приложения право показывать уведомления (до API 33 — всегда). */
fun Context.hasNotificationPermission(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
