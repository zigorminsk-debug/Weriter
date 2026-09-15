# ZI Writer

Android-приложение (Kotlin) для пересказа/доработки текста в книгу: обучение на
книгах (FB2/EPUB/TXT, OPDS Флибусты, Gutendex, Open Library), математическая модель
сюжета, экспорт в TXT/PDF/DOCX/EPUB, обложки и иллюстрации, озвучка (TTS),
библиотека готовых работ, покрытие клавиатуры.

* Исходники: `zi-writer/` (Gradle-проект, модуль `app`)
* Пакет: `com.zakharevich.ziwriter`
* minSdk 24, targetSdk 34, Java/Kotlin 17
* Отчёт о найденных ошибках и правках: [`docs/CODE_REVIEW.md`](docs/CODE_REVIEW.md)

---

## Автоматическая сборка APK (GitHub Actions)

### 1. Сборка при публикации релиза → файл прикрепляется к релизу

Workflow: **`.github/workflows/android-release.yml`**

Запускается автоматически, когда вы нажимаете **Publish release** (событие
`release: published`), а также вручную: **Actions → Release APK → Run workflow**
(можно указать тег).

Что происходит:

1. Читается тег релиза (`v31.0`, `31.0`, `v32` …).
2. Из тега вычисляются `versionName` и `versionCode`
   (`versionCode = major*10000 + minor*100 + patch`, например v31.0 → `310000`).
3. Собирается подписанный `app-release.apk`.
4. Файл переименовывается в `ZI-Writer-v<версия>.apk`, к нему считается
   `SHA256SUMS.txt`.
5. Файлы прикрепляются к тому же GitHub Release (если релиза ещё нет — создаётся).
6. APK и контрольная сумма остаются в артефактах Actions на 90 дней.

**Порядок работы:** создали тег → создали Release (можно черновик) → нажали Publish —
через несколько минут в релизе появится готовый APK.

### 2. Проверка сборки на каждый push / pull request

Workflow: **`.github/workflows/android-build.yml`** — собирает debug APK. Если код
не компилируется, вы увидите это сразу в PR, а не при выпуске релиза.

---

## Секреты репозитория (Settings → Secrets and variables → Actions)

Для подписи релиза **рекомендуется** завести:

| Секрет | Что это | Как получить |
|---|---|---|
| `KEYSTORE_BASE64` | keystore в base64 | `base64 -w0 zi-writer-release.jks` |
| `KEYSTORE_PASSWORD` | пароль хранилища | — |
| `KEY_ALIAS` | алиас ключа (`ziwriter`) | `keytool -list -v -keystore zi-writer-release.jks` |
| `KEY_PASSWORD` | пароль ключа | — |

Если секретов нет, workflow соберёт APK на ключе из репозитория
(`zi-writer/zi-writer-release.jks`) и напечатает предупреждение — сборка не упадёт.

> ⚠️ Ключ подписи и пароли ранее хранились прямо в репозитории. Это значит, что
> любой, у кого есть доступ к репозиторию, может подписать своим APK обновление
> для вашего приложения. Правильнее ключ из репозитория убрать, а новые версии
> подписывать ключом из секретов (в перспективе — выпустить новый ключ подписи).

Для локальной сборки подпись настраивается файлом `zi-writer/keystore.properties`
(в `.gitignore`), например:

```properties
storeFile=../zi-writer-release.jks
storePassword=...
keyAlias=ziwriter
keyPassword=...
```

---

## Локальная сборка

```bash
cd zi-writer
echo "sdk.dir=$ANDROID_HOME" > local.properties   # путь к Android SDK
./gradlew assembleDebug        # debug APK: app/build/outputs/apk/debug/
./gradlew assembleRelease      # release APK: app/build/outputs/apk/release/
```

Версию можно переопределить без правки файлов:

```bash
./gradlew assembleRelease -PziwriterVersionName=32.0 -PziwriterVersionCode=320000
```

## Структура исходников

| Файл | Назначение |
|---|---|
| `MainActivity.kt` | экран приложения: ввод, генерация, экспорт, обучение, поиск книг |
| `StoryGenerator.kt` | генерация текста (жанр, стиль, герои, сюжет) |
| `MathPlotModel.kt` | математическая модель сюжета и подбора плана |
| `LearningManager.kt` | база обучения: книги, герои, места, обороты, сюжеты |
| `GrammarCorrector.kt` | автокоррекция типовых ошибок |
| `OpdsFetcher.kt` / `InternetBookFetcher.kt` | каталоги книг (OPDS, Gutendex, Open Library, Wikipedia) |
| `Fb2Importer.kt` / `EpubImporter.kt` | импорт книг FB2/EPUB |
| `PdfExporter.kt` / `DocxExporter.kt` / `EpubExporter.kt` | экспорт результата |
| `CoverGenerator.kt` / `ChapterIllustrator.kt` | обложки и иллюстрации глав |
| `DownloadSaver.kt` | сохранение файлов в «Загрузки» (MediaStore) |
| `HistoryManager.kt` | библиотека готовых работ |
