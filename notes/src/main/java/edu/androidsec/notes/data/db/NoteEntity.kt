package edu.androidsec.notes.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import edu.androidsec.notes.domain.Note
import edu.androidsec.notes.domain.NoteValidator

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val body: String,
    @ColumnInfo(name = "photo_path") val photoPath: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    /** Теги одной строкой через пробел («kotlin room-2»); пустая строка — тегов нет. */
    @ColumnInfo(name = "tags", defaultValue = "''") val tags: String = "",
    @ColumnInfo(name = "is_secret", defaultValue = "0") val isSecret: Boolean = false,
    /**
     * Метка «удалена, но ещё можно отменить» (время удаления). null — заметка живая.
     * Используется удалением с отменой; настоящее удаление строки происходит позже.
     */
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)

fun NoteEntity.toDomain(): Note = Note(
    id = id,
    title = title,
    body = body,
    photoPath = photoPath,
    updatedAt = updatedAt,
    tags = NoteValidator.parseTags(tags),
    isSecret = isSecret,
)

fun Note.toEntity(): NoteEntity = NoteEntity(
    id = id,
    title = title,
    body = body,
    photoPath = photoPath,
    updatedAt = updatedAt,
    tags = NoteValidator.formatTags(tags),
    isSecret = isSecret,
)
