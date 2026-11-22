# Лабораторная работа #2. Разрешения, компоненты и IPC

## Фиксированные идентификаторы

Их нельзя менять: по ним работают `scripts/check.sh` и клиент преподавателя.

| Что | Значение |
|---|---|
| applicationId «Заметок» | `edu.androidsec.notes` |
| applicationId клиента | `edu.androidsec.notes.client` |
| Deep link | `notes://note/<id>` |
| Authority FileProvider | `edu.androidsec.notes.files` |
| Authority провайдера заголовков | `edu.androidsec.notes.provider`, путь `/notes` |
| Signature-разрешение | `edu.androidsec.notes.permission.READ_NOTES` |
| Класс ресивера | `edu.androidsec.notes.reminder.ReminderActionReceiver` |
| Действие ресивера | `edu.androidsec.notes.action.DONE` |
| Id канала уведомлений | `reminders` |

## Что где доделывать

Каждый пункт задания помечен в коде комментарием `TODO(ipc-N)`. В комментарии написано, что
сделать, к какому блоку лекции это относится и каким шагом чек-листа проверяется.

| TODO | Файлы | Состояние в заготовке |
|---|---|---|
| ipc-1 | `notes/src/main/AndroidManifest.xml` | закомментированный блок разрешений: нужные и ненужные; `exported` только у лаунчера |
| ipc-2 | `reminder/ReminderPermission.kt`, `photo/PhotoPicker.kt` | функции с сигнатурами и заглушками, кнопки показывают Toast «TODO(ipc-2)» |
| ipc-3 | манифест, `deeplink/NoteDeepLink.kt`, `ui/note/NoteViewModel.kt` | нет intent-filter, `parse()` ничего не разбирает, несуществующий id не обрабатывается |
| ipc-4 | `reminder/ReminderScheduler.kt`, `reminder/ReminderActionReceiver.kt` | уведомление без `PendingIntent`, пустой `onReceive`, ресивера нет в манифесте |
| ipc-5 | `share/ShareNote.kt`, `res/xml/file_paths.xml` | функция пишет файл в `cacheDir/exports/` и возвращает его; `file_paths.xml` нет; провайдера нет в манифесте |
| ipc-6 | `provider/TitlesProvider.kt`, манифест | `query()` возвращает `null`; провайдера и `<permission>` нет в манифесте |

Что уже работает и трогать не нужно: список и экран заметки, Room (три стартовые заметки с
id 1, 2, 3), канал уведомлений, копирование выбранного фото во внутреннее хранилище
(`photo/PhotoStore.kt`), запись заметки в текстовый файл (`share/NoteExporter.kt`),
модуль `:client` целиком.

### Как устроен код

Один Gradle-проект, два application-модуля, только AndroidX и Kotlin. Каждый экран — это пара
файлов в `ui/<экран>/`:

- `…ViewModel.kt` держит состояние экрана в `StateFlow` и вызывает репозиторий
  (`data/NotesRepository.kt`, под ним Room). Одноразовые события — «показать сообщение»,
  «закрыть экран» — уходят через `Channel`.
- `…Screen.kt` (Compose) рисует состояние и вызывает методы ViewModel. Системные диалоги
  (запрос разрешения, выбор фото, «Поделиться») запускаются отсюда, потому что им нужна Activity.

Зависимости собираются вручную в `di/AppContainer.kt`.

## Окружение

- Android Studio актуальной версии. Gradle-демону нужна Java 25
  (`gradle/gradle-daemon-jvm.properties`); если её нет, Gradle скачает её сам при первой
  сборке — для этого один раз нужен интернет.
- **Эмулятор**: образ **Google Play**, API 34 или новее (заготовка проверена на API 36,
  Pixel 6). Обычный образ без Play тоже подойдёт, но в нём `adb root` доступен, и часть
  проверок теряет смысл.
- В SDK Manager включите **Android SDK Command-line Tools**: оттуда берётся `apkanalyzer`
  для `scripts/check.sh`.
- Сборка: `./gradlew :notes:assembleDebug :client:assembleDebug` (Windows: `gradlew.bat`).
  AGP 9.4, Kotlin 2.4, compileSdk 37, minSdk 26.
- Фото для проверки Photo Picker на эмуляторе: закинуть любую картинку и попросить систему
  её увидеть:

  ```bash
  adb push photo.jpg /sdcard/Pictures/photo.jpg
  adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Pictures/photo.jpg
  ```

## Скрипт проверки

`scripts/check.sh` запускает adb-часть чек-листа. Нужны запущенный эмулятор с установленными
«Заметками», переменная `ANDROID_HOME` (или SDK в стандартном месте) и **bash** (на Windows —
Git Bash; на macOS запускайте именно `bash scripts/check.sh`, не `zsh`).

```bash
bash scripts/check.sh all
```

Отдельно: `manifest` (шаг 1), `closed` (шаг 2), `deeplink` (шаг 4), `provider` (шаг 7).
Каждая проверка печатает `PASS`/`FAIL` и сырой вывод adb под ним; код возврата 0 — все
PASS. На заготовке скрипт обязан падать по всем шагам безопасности: заглушки настоящие.

Что делает скрипт:

- `manifest` — скачивает установленный APK, печатает merged manifest через `apkanalyzer`,
  проверяет `exported` у каждого компонента, список экспортированных (допустимы
  `MainActivity`, `TitlesProvider` и компоненты, закрытые разрешением уровня `signature`, —
  они отмечаются `NOTE`), объявление ресивера, `FileProvider`, провайдера с
  signature-разрешением и список `uses-permission` (допустимо только `POST_NOTIFICATIONS`).
- `closed` — два `am broadcast` с действием «Готово»: явный на ресивер и в пакет приложения.
  Ресивер не должен среагировать: в logcat не должно появиться ни одной строки с тегом
  `ReminderActionReceiver`. Затем `content read` на URI FileProvider (должен быть
  `Permission Denial`).
- `deeplink` — `am start -d notes://note/{1,999999,abc,}`; после каждого смотрит logcat на
  `FATAL EXCEPTION`. Если ваш intent-filter строже и ссылки с `abc` или пустым id вообще не
  открываются (`unable to resolve Intent`), это тоже PASS; для `1` и `999999` — FAIL.
- `provider` — `content query`, `content insert`, `content delete`; все должны быть отклонены.

Подсказка к шагу 1: merged manifest — это не ваш `AndroidManifest.xml`, а его слияние с
манифестами библиотек. Посмотрите его в Android Studio (вкладка *Merged Manifest*) или
выводом `check.sh manifest`: библиотеки добавляют свои компоненты, и среди них может быть
экспортированный. Для каждого такого компонента разберитесь, кто может его вызвать. Если он
закрыт разрешением уровня `signature`, это не дыра, и скрипт его пропустит с пометкой `NOTE`.
Если компонент приложению не нужен, его можно убрать из итогового манифеста через
`tools:node="remove"`; на защите будьте готовы объяснить, зачем.

## Клиент с другим ключом

По умолчанию оба модуля подписываются debug-ключом Android Studio
(`~/.android/debug.keystore`), поэтому у вас клиент видит заголовки. Преподаватель соберёт
тот же модуль своим ключом и должен увидеть `SecurityException`. Проверьте это сами:

```bash
keytool -genkeypair -keystore other.jks -storepass other123 -keypass other123 -alias other -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Other, O=Test, C=RU"
./gradlew :client:assembleDebug -Psigning.storeFile=/абсолютный/путь/other.jks -Psigning.storePassword=other123 -Psigning.keyAlias=other -Psigning.keyPassword=other123
adb uninstall edu.androidsec.notes.client
adb install client/build/outputs/apk/debug/client-debug.apk
```

Вместо `-P` можно положить те же четыре параметра в `client/signing.properties`
(файл в `.gitignore`). Удалять установленный клиент перед установкой обязательно: подпись
изменилась, а обновление поверх старой подписи система не разрешает. Чтобы вернуть
debug-подпись — собрать без параметров и снова `uninstall` + `install`.

## Флоу работы

- Создайте свой репозиторий из ветки `starter` (fork или `git clone` + `git push` в свой
  remote) и работайте в нём. **История ветки `starter` должна сохраниться**: не пересоздавайте
  репозиторий и не схлопывайте коммиты заготовки — по истории видно, что именно сделали вы.
- Ссылку на репозиторий загрузите в Moodle до дедлайна. В README своего репозитория добавьте
  раздел с командами adb и их выводом для пунктов 3–6.
- Не коммитьте `local.properties`, ключи (`*.jks`) и `client/signing.properties`.


---

# Отчёт: доказательства закрытых точек входа (пункты 3–6)

> Вывод команд ниже нужно вставить из своего эмулятора (Google Play, API 34+) после установки
> сборки: `./gradlew :notes:installDebug :client:installDebug`. Блоки с `<вставить вывод>`
> заполняются реальным выводом, не пересказом.

## Пункт 3. Deep link

```bash
adb shell am start -a android.intent.action.VIEW -d "notes://note/1"
adb shell am start -a android.intent.action.VIEW -d "notes://note/999999"
adb shell am start -a android.intent.action.VIEW -d "notes://note/abc"
adb shell am start -a android.intent.action.VIEW -d "notes://note/"
adb logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime"
```

```

$ adb shell am start -a android.intent.action.VIEW -d "notes://note/1"
Starting: Intent { act=android.intent.action.VIEW dat=notes://note/... }
Warning: Activity not started, its current task has been brought to the front


$ adb shell am start -a android.intent.action.VIEW -d "notes://note/999999"
Starting: Intent { act=android.intent.action.VIEW dat=notes://note/... }
Warning: Activity not started, intent has been delivered to currently running top-most instance.


$ adb shell am start -a android.intent.action.VIEW -d "notes://note/abc"
Starting: Intent { act=android.intent.action.VIEW dat=notes://note/... }
Warning: Activity not started, intent has been delivered to currently running top-most instance.


$ adb shell am start -W -a android.intent.action.VIEW -d "notes://note/"
Starting: Intent { act=android.intent.action.VIEW dat=notes://note/... }
Warning: Activity not started, intent has been delivered to currently running top-most instance.
Status: ok
LaunchState: UNKNOWN (0)
Activity: edu.androidsec.notes/.MainActivity
TotalTime: 0
WaitTime: 14
Complete


$ adb logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime"
11-20 18:42:22.912 12240 12240 I AndroidRuntime: VM exiting with result code 0, cleanup skipped.

```
Что видно на экране: `1` — открывается заметка; `999999` — «Заметка не найдена», редактор не
открывается; `abc` и пустой id — «Некорректная ссылка на заметку».

## Пункт 4. Ресивер «Готово» закрыт

```bash
adb logcat -c
adb shell am broadcast -n edu.androidsec.notes/.reminder.ReminderActionReceiver -a edu.androidsec.notes.action.DONE --el edu.androidsec.notes.extra.NOTE_ID 1
adb shell am broadcast -a edu.androidsec.notes.action.DONE -p edu.androidsec.notes --el edu.androidsec.notes.extra.NOTE_ID 1
adb logcat -d | grep -E "DONE received|[VDIWEF] ReminderActionReceiver *:"
```

```

$ adb shell am broadcast -n edu.androidsec.notes/.reminder.ReminderActionReceiver -a edu.androidsec.notes.action.DONE --el edu.androidsec.notes.extra.NOTE_ID 1
Broadcasting: Intent { act=edu.androidsec.notes.action.DONE flg=0x400000 cmp=edu.androidsec.notes/.reminder.ReminderActionReceiver (has extras) }
Broadcast completed: result=0


$ adb shell am broadcast -a edu.androidsec.notes.action.DONE -p edu.androidsec.notes --el edu.androidsec.notes.extra.NOTE_ID 1
Broadcasting: Intent { act=edu.androidsec.notes.action.DONE flg=0x400000 pkg=edu.androidsec.notes (has extras) }
Broadcast completed: result=0


$ adb logcat -d | grep -E "DONE received|[VDIWEF] ReminderActionReceiver *:"



```

Чтобы показать, что «Готово» из самого уведомления работает: нажать «Напомнить», дождаться
уведомления, нажать «Готово» и выполнить `adb logcat -d | grep "DONE received"`.

```

```

## Пункт 5. FileProvider не читается снаружи

```bash
adb shell content read --uri content://edu.androidsec.notes.files/exports/1.txt
```

```

$ adb shell content read --uri content://edu.androidsec.notes.files/exports/1.txt
Error while accessing provider:edu.androidsec.notes.files
java.lang.SecurityException: Permission Denial: opening provider androidx.core.content.FileProvider from (null) (pid=21360, uid=2000) that is not exported from UID 10411

```

## Пункт 6. Провайдер заголовков только для своих

```bash
adb shell content query  --uri content://edu.androidsec.notes.provider/notes
adb shell content insert --uri content://edu.androidsec.notes.provider/notes --bind title:s:hacked
adb shell content delete --uri content://edu.androidsec.notes.provider/notes
adb shell am start -n edu.androidsec.notes.client/.MainActivity
adb logcat -d -s TitlesProvider
adb shell dumpsys package edu.androidsec.notes.client | grep READ_NOTES

```

```

$ adb shell content query  --uri content://edu.androidsec.notes.provider/notes
Error while accessing provider:edu.androidsec.notes.provider
java.lang.SecurityException: Permission Denial: opening provider edu.androidsec.notes.provider.TitlesProvider from (null) (pid=25055, uid=2000) requires edu.androidsec.notes.permission.READ_NOTES or edu.androidsec.notes.permission.READ_NOTES




$ adb shell content insert --uri content://edu.androidsec.notes.provider/notes --bind title:s:hacked
Error while accessing provider:edu.androidsec.notes.provider
java.lang.SecurityException: Permission Denial: opening provider edu.androidsec.notes.provider.TitlesProvider from (null) (pid=25084, uid=2000) requires edu.androidsec.notes.permission.READ_NOTES or edu.androidsec.notes.permission.READ_NOTES





$ adb shell content delete --uri content://edu.androidsec.notes.provider/notes
Error while accessing provider:edu.androidsec.notes.provider
java.lang.SecurityException: Permission Denial: opening provider edu.androidsec.notes.provider.TitlesProvider from (null) (pid=25107, uid=2000) requires edu.androidsec.notes.permission.READ_NOTES or edu.androidsec.notes.permission.READ_NOTES

$ adb shell am start -n edu.androidsec.notes.client/.MainActivity
Starting: Intent { cmp=edu.androidsec.notes.client/.MainActivity }

$ adb logcat -d -s TitlesProvider
--------- beginning of main
11-20 19:29:28.771  4129 25066 I TitlesProvider: query from uid=10472 package=edu.androidsec.notes.client
11-20 19:29:30.211  4129 25066 I TitlesProvider: query from uid=10472 package=edu.androidsec.notes.client


$ adb shell dumpsys package edu.androidsec.notes.client | grep READ_NOTES
      edu.androidsec.notes.permission.READ_NOTES
      edu.androidsec.notes.permission.READ_NOTES: granted=true

```


`adb logcat -d | grep -i SecurityException`.



## `bash scripts/check.sh all`



---

# Лабораторная работа #3. Ввод данных, SQL, удаление с отменой, секретные заметки

Ветка `lab3` от `main` (ЛР2). Проверки ЛР2 не нарушены: `bash scripts/check.sh all` по-прежнему даёт `PASS=18 FAIL=0`.

## Что сделано по пунктам задания

### 1. Поля ввода (`ui/note/NoteForm.kt`, `NoteScreen.kt`)
- Поля заголовка, текста и тегов переведены на `TextFieldState`.
- Ввод ограничен `InputTransformation` по принципу белого списка: если после правки текст недопустим, правка откатывается целиком (`revertAllChanges`), а не «исправляется».
- Правила: заголовок до 100 символов и одна строка; текст до 5000 символов; до 5 тегов через пробел, каждый 1–20 символов из `a-z`, `0-9`, `-`.
- Причина отказа показывается под полем (красная рамка и текст), в обычном состоянии там счётчик или подсказка.
- Длина считается в символах Unicode (code point), поэтому эмодзи занимает один символ.
- Если заметка из старой версии уже длиннее лимита, её можно только укорачивать, иначе привести её в порядок было бы нельзя.

### 2. Проверка в репозитории (`domain/NoteValidator.kt`, `data/NotesRepository.kt`)
- Правила вынесены в класс `NoteValidator`, который не зависит от Android. Его вызывают и поля ввода, и репозиторий.
- `RoomNotesRepository.save()` проверяет заметку ещё раз и бросает `InvalidNoteException`; в базу некорректная заметка не попадает, даже если обойти экран.
- Тесты: `NoteValidatorTest` (допустимые значения, превышение длины, неверные символы, неверный тег, слишком много тегов), `NoteSearchTest.save_rejectsInvalidNotes_andWritesNothing` (проверка на реальной базе).

### 3. Миграция Room 1 → 2 (`data/db/NotesDatabase.kt`)
- Версия базы 2, `Migration(1, 2)` добавляет колонки `tags`, `is_secret` и `deleted_at` через `ALTER TABLE ... ADD COLUMN` с `DEFAULT`; старые заметки сохраняются.
- `fallbackToDestructiveMigration` не используется.
- Тест `NoteSearchTest.migration_1_2_keepsExistingNotes` создаёт настоящий файл базы версии 1, открывает его новой версией и проверяет, что заметка и её поля на месте.

### 4. Поиск и сортировка (`ui/search/`, `data/db/NoteDao.kt`, `data/db/SearchQuery.kt`)
- Экран поиска открывается из списка (иконка лупы), маршрут `SearchRoute` в `NotesNavHost`.
- Поиск по заголовку и тексту: `LIKE :pattern ESCAPE '\'`. Символы `%`, `_` и `\` экранируются в `SearchQuery.escape`, шаблон передаётся параметром, в SQL-строку пользовательский ввод не подставляется.
- Сортировка выбирается из enum `SearchSort` (по заголовку, сначала новые, сначала старые), каждому варианту соответствует свой готовый `@Query`. `@RawQuery` и подстановки имени колонки нет.
- Тесты Room: `NoteSearchTest` (буквальный поиск `%`, `_`, `\`, ввод вида `' OR 1=1 --`, три сортировки, удалённые и секретные заметки), `SearchQueryTest` (экранирование).

### 5. Удаление с отменой (`delete/NoteDeleter.kt`)
- «Удалить» (из списка и с экрана заметки) только помечает заметку (`deleted_at`): одна атомарная запись, строка и файл фото остаются нетронутыми. Заметка исчезает из списка, поиска и провайдера заголовков.
- Снекбар «Заметка удалена» с кнопкой «Отменить». Через 5 секунд строка и файл фото удаляются окончательно; «Отменить» сбрасывает метку, и заметка возвращается целиком.
- Таймер живёт в объекте уровня приложения, а не во ViewModel экрана, поэтому не пропадает при уходе с экрана.
- Если приложение закрыли в эти 5 секунд, помеченная строка остаётся в базе целиком, а при следующем запуске доудаляется вместе с фото (`purgeLeftovers`). Заметка не бывает «наполовину удалённой»: до удаления строки файл фото на месте.
- Гонка «Отменить» и таймера исключена: кто первым достал задачу из таблицы, тот и владеет заметкой; SQL-запросы удаления и возврата условные (`WHERE deleted_at IS NOT NULL`).
- Тесты: `NoteDeletionTest` (скрытие, полный возврат, окончательное удаление, опоздавший таймер, остатки после убитого процесса).

### 6. Секретные заметки
- **Экран** (`ui/note/SecureWindow.kt`): пока открыта секретная заметка, на окне стоит `FLAG_SECURE` (нет скриншотов, записи экрана и миниатюры в «Недавних»), плюс `setRecentsScreenshotEnabled(false)` на Android 13+. Флаг ставится в самом экране заметки, поэтому работает при любом входе: из списка, по deep link и из уведомления. Пока заметка загружается, окно тоже защищено. Флаг держит счётчик, чтобы переход между двумя секретными заметками не снял защиту с новой. У обычных заметок скриншоты работают.
- **Список** (`ui/list/NotesListScreen.kt`): у секретной заметки показываются только заголовок и иконка замка, без текста и тегов. То же в результатах поиска.
- **Буфер обмена** (`clipboard/NoteClipboard.kt`): кнопка «Копировать текст»; для секретной заметки в `ClipData` добавляется флаг `IS_SENSITIVE`. Тест `NoteClipboardTest` проверяет флаг системной константой.
- **Напоминание** (`reminder/ReminderScheduler.kt`): у секретной заметки в уведомлении нет заголовка (нейтральный текст) и стоит `VISIBILITY_SECRET`. Секретность проверяется в момент показа, а не только при планировании.
- **Решения сверх задания:** `TitlesProvider` не отдаёт секретные заметки, а поиск не ищет по тексту секретных заметок (иначе скрытый текст можно подбирать поиском).

## Как проверить

```bash
./gradlew :notes:testDebugUnitTest          # unit-тесты: правила ввода, экранирование поиска
./gradlew :notes:connectedDebugAndroidTest  # тесты на телефоне: Room, поиск, миграция, удаление, буфер
bash scripts/check.sh all                   # проверки ЛР2 (PASS=18 FAIL=0)
```

Примечание: `connectedDebugAndroidTest` по окончании удаляет приложение с устройства вместе с данными; после него приложение нужно установить заново (`./gradlew :notes:installDebug :client:installDebug`).

Ручные проверки: поля ввода (лимиты, теги, подсказки под полями); поиск (`%`, `_`, `\` буквально, три сортировки); удаление (снекбар, «Отменить», `am force-stop` в окне отмены, файл фото в `files/photos` через `adb shell run-as`); секретная заметка (скриншот заблокирован при входе из списка, по `adb shell am start ... notes://note/<id>` и из уведомления; кадр `adb exec-out screencap` чёрный; в списке нет текста; уведомление без заголовка).

## Известные ограничения

- «Поделиться файлом» отправляет содержимое и секретной заметки: это явное действие пользователя.
- При двух удалениях подряд снекбар показывается только у последней заметки; первая удаляется окончательно по своему таймеру.
- Если напоминание сработало, пока заметка ждёт конца окна отмены, оно не показывается.
- Подтверждение копирования рисует оболочка телефона. На Samsung оно может не показывать разницы между обычной и чувствительной заметкой; наличие флага проверяет `NoteClipboardTest`.
- Сортировка «по заголовку» использует `COLLATE NOCASE`; регистр для кириллицы в SQLite этим не сворачивается, порядок заголовков, начинающихся со строчной и заглавной буквы, может отличаться от алфавитного.
