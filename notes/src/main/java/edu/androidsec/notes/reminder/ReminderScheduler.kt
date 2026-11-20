package edu.androidsec.notes.reminder

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import edu.androidsec.notes.MainActivity
import edu.androidsec.notes.R
import edu.androidsec.notes.deeplink.NoteDeepLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Планировщик напоминаний. Для лабораторной достаточно задержки в корутине уровня процесса:
 * настоящие напоминания делаются через AlarmManager/WorkManager, это тема другой работы.
 *
 * Уведомление (ipc-4): оба PendingIntent создаются с `FLAG_IMMUTABLE` и ЯВНЫМ интентом на
 * свой компонент. Неизменяемый PendingIntent важен: получатель (система, другое приложение,
 * которому его передали) не сможет подменить компонент, действие или extras. Неявный интент
 * с изменяемым PendingIntent позволил бы перенаправить его в чужой компонент от имени «Заметок».
 *
 * Request code равен id заметки: PendingIntent различаются по интенту БЕЗ учёта extras,
 * поэтому без разных request code «Готово» от второй заметки обновило бы первую.
 */
class ReminderScheduler(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val jobs = HashMap<Long, Job>()

    fun schedule(noteId: Long, title: String, delayMillis: Long = DEFAULT_DELAY_MILLIS) {
        val text = title.ifBlank { context.getString(R.string.note_untitled) }
        synchronized(jobs) {
            jobs.remove(noteId)?.cancel()
            jobs[noteId] = scope.launch {
                delay(delayMillis)
                showNotification(noteId, text)
            }
        }
    }

    fun cancel(noteId: Long) {
        synchronized(jobs) { jobs.remove(noteId)?.cancel() }
    }

    @SuppressLint("MissingPermission") // наличие POST_NOTIFICATIONS проверяется строкой ниже
    private fun showNotification(noteId: Long, title: String) {
        if (!context.hasNotificationPermission()) {
            Log.w(TAG, "POST_NOTIFICATIONS not granted, reminder for note $noteId dropped")
            return
        }

        val requestCode = noteId.toInt()

        // Нажатие на уведомление: явный интент на MainActivity + deep link на заметку.
        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = NoteDeepLink.uriFor(noteId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val openPending = PendingIntent.getActivity(
            context,
            requestCode,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        // Кнопка «Готово»: явный интент на закрытый (exported=false) ресивер.
        val doneIntent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = ReminderActionReceiver.ACTION_DONE
            putExtra(ReminderActionReceiver.EXTRA_NOTE_ID, noteId)
        }
        val donePending = PendingIntent.getBroadcast(
            context,
            requestCode,
            doneIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.reminder_notification_title))
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openPending)
            .addAction(
                R.drawable.ic_notification,
                context.getString(R.string.reminder_action_done),
                donePending,
            )
            .build()

        NotificationManagerCompat.from(context).notify(noteId.toInt(), notification)
    }

    companion object {
        const val TAG = "ReminderScheduler"
        /** Id канала фиксирован заданием. */
        const val CHANNEL_ID = "reminders"
        const val DEFAULT_DELAY_MILLIS = 10_000L
    }
}
