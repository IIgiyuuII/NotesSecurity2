package edu.androidsec.notes.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import edu.androidsec.notes.R
import edu.androidsec.notes.appContainer

/**
 * Ресивер кнопки «Готово» в уведомлении (ipc-4).
 *
 * В манифесте он `exported="false"`: вызвать его может только наш PendingIntent (система
 * отправляет его от имени приложения). Но и закрытый ресивер проверяет вход: действие и extras
 * считаются недоверенным вводом.
 */
class ReminderActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DONE) {
            Log.w(TAG, "Ignoring unexpected action: ${intent.action}")
            return
        }
        val noteId = intent.getLongExtra(EXTRA_NOTE_ID, INVALID_ID)
        if (noteId <= 0L) {
            Log.w(TAG, "DONE without a valid note id")
            return
        }

        Log.i(TAG, "DONE received for note $noteId")
        NotificationManagerCompat.from(context).cancel(noteId.toInt())
        context.appContainer.reminderScheduler.cancel(noteId)
        Toast.makeText(context, R.string.reminder_done_toast, Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val TAG = "ReminderActionReceiver"
        const val ACTION_DONE = "edu.androidsec.notes.action.DONE"
        const val EXTRA_NOTE_ID = "edu.androidsec.notes.extra.NOTE_ID"
        private const val INVALID_ID = -1L
    }
}
