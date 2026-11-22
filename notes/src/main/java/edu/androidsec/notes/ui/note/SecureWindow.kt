package edu.androidsec.notes.ui.note

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Защита окна от скриншотов, записи экрана и миниатюры в «Недавних» (`FLAG_SECURE`).
 *
 * Флаг держит счётчик, а не простое «включить/выключить»: если с одной защищённой заметки
 * переходят на другую (например, по уведомлению), уход со старого экрана не должен снять флаг
 * с нового. Флаг снимается, только когда защищённых экранов не осталось.
 * Вызывается только из главного потока (из composable).
 */
object SecureWindow {
    private var holders = 0

    fun acquire(activity: Activity) {
        holders++
        apply(activity)
    }

    fun release(activity: Activity) {
        if (holders > 0) holders--
        apply(activity)
    }

    private fun apply(activity: Activity) {
        val secure = holders > 0
        if (secure) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        // Дополнительно к FLAG_SECURE: система не снимает снимок для списка недавних (Android 13+).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.setRecentsScreenshotEnabled(!secure)
        }
    }
}

/**
 * Пока [enabled] истинно и composable на экране, окно Activity защищено [SecureWindow].
 * Эффект живёт внутри экрана заметки, поэтому защита работает при любом входе: из списка,
 * по deep link и из уведомления.
 */
@Composable
fun SecureWindowEffect(enabled: Boolean) {
    val context = LocalContext.current
    DisposableEffect(enabled, context) {
        val activity = context.findActivity()
        if (enabled && activity != null) SecureWindow.acquire(activity)
        onDispose {
            if (enabled && activity != null) SecureWindow.release(activity)
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
