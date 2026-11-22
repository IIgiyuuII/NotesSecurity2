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
import edu.androidsec.notes.data.NotesRepository
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
 *
 * Секретные заметки (ЛР3): заголовок такой заметки в уведомление не попадает, а само
 * уведомление скрыто на экране блокировки. Секретность проверяется в момент показа, а не
 * только при планировании: заметку могли пометить секретной уже после нажатия «Напомнить».
 */
class ReminderScheduler(
    private val context: Context,
    private val scope: CoroutineScope,
    private val repository: NotesRepository,
) {
    private val jobs = HashMap<Long, Job>()

    fun schedule(
        noteId: Long,
        title: String,
        isSecret: Boolean,
        delayMillis: Long = DEFAULT_DELAY_MILLIS,
    ) {
        val text = title.ifBlank { context.getString(R.string.note_untitled) }
        synchronized(jobs) {
            jobs.remove(noteId)?.cancel()
            jobs[noteId] = scope.launch {
                delay(delayMillis)
                // Заметки уже нет (удалена, в том числе ждёт окна отмены): напоминать не о чем.
                val note = repository.get(noteId) ?: return@launch
                val secret = isSecret || note.isSecret
                showNotification(noteId, if (secret) null else text)
            }
        }
    }

    fun cancel(noteId: Long) {
        synchronized(jobs) { jobs.remove(noteId)?.cancel() }
    }

    /** [title] == null: заметка секретная, заголовок показывать нельзя. */
    @SuppressLint("MissingPermission") // наличие POST_NOTIFICATIONS проверяется строкой ниже
    private fun showNotification(noteId: Long, title: String?) {
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

        val secret = title == null
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.reminder_notification_title))
            .setContentText(title ?: context.getString(R.string.reminder_secret_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openPending)
            .addAction(
                R.drawable.ic_notification,
                context.getString(R.string.reminder_action_done),
                donePending,
            )
        if (secret) {
            // Секретное уведомление не показывается на экране блокировки вовсе.
            builder.setVisibility(NotificationCompat.VISIBILITY_SECRET)
        }

        NotificationManagerCompat.from(context).notify(noteId.toInt(), builder.build())
    }

    companion object {
        const val TAG = "ReminderScheduler"
        /** Id канала фиксирован заданием. */
        const val CHANNEL_ID = "reminders"
        const val DEFAULT_DELAY_MILLIS = 10_000L
    }
}
