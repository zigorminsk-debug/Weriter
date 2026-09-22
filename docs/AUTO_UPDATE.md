# Автоматическое обновление через GitHub Releases

## 📋 Обзор

Приложение ZI Writer поддерживает автоматическую проверку и установку обновлений через GitHub Releases. При запуске приложение проверяет наличие новой версии на GitHub и предлагает пользователю обновиться.

## 🔄 Как это работает

### Процесс обновления

1. **Проверка версии** (при каждом запуске приложения)
   - Приложение получает текущую версию из `build.gradle` (`versionName` и `versionCode`)
   - Запрашивает информацию о последнем релизе через GitHub API: `https://api.github.com/repos/zigorminsk-debug/Weriter/releases/latest`
   - Сравнивает версии

2. **Уведомление пользователя**
   - Если доступна новая версия — показывается диалог с предложением обновиться
   - Показываются: новая версия, текущая версия, список изменений (release notes)
   - Пользователь может выбрать: "Обновить", "Позже" или "Открыть на GitHub"

3. **Скачивание APK**
   - Используется системный `DownloadManager` Android
   - APK скачивается из assets релиза (ищется файл с расширением `.apk`)
   - Пользователь видит прогресс загрузки в уведомлениях

4. **Установка**
   - После загрузки автоматически запускается системный инсталлятор
   - На Android 8+ требуется разрешение "Установка из неизвестных источников"
   - Приложение само открывает настройки для получения разрешения (если нужно)

## 📦 Создание релиза на GitHub

### Шаг 1: Подготовка APK

Соберите release APK:
```bash
cd zi-writer
./gradlew assembleRelease
```

APK будет в: `zi-writer/app/build/outputs/apk/release/app-release.apk`

### Шаг 2: Создание релиза на GitHub

1. Перейдите на страницу репозитория: https://github.com/zigorminsk-debug/Weriter/releases
2. Нажмите **"Draft a new release"**
3. Заполните поля:
   - **Tag**: `32.0` (или `v32.0`) — номер версии
   - **Title**: `ZI Writer v32.0`
   - **Description**: Список изменений (markdown поддерживается)
     ```markdown
     ## Что нового
     - Исправлены ошибки генерации текста
     - Добавлена мат. модель сюжета
     - Улучшена работа с OPDS
     ```
4. **Прикрепите APK файл**:
   - Нажмите "Attach binaries by dropping them below"
   - Перетащите `app-release.apk`
   - Важно: файл должен иметь расширение `.apk`
5. Нажмите **"Publish release"**

### Шаг 3: Проверка

После публикации релиза:
- Откройте приложение ZI Writer
- Оно автоматически проверит обновления при запуске
- Должен появиться диалог с предложением обновиться

## 🔧 Настройка

### Изменение репозитория

Если приложение переедет в другой репозиторий, измените константы в `UpdateManager.kt`:

```kotlin
private const val GITHUB_OWNER = "zigorminsk-debug"
private const val GITHUB_REPO = "Weriter"
```

### Формат версии

Приложение использует формат `major.minor.patch`:
- `32.0` → versionCode: 320000
- `32.1.2` → versionCode: 320102

Формула: `major * 10000 + minor * 100 + patch`

### Обновление versionCode в build.gradle

Перед созданием релиза увеличьте версию в `app/build.gradle`:

```gradle
def ziVersionName = '32.0'        // Новая версия
def ziVersionCode = 320000        // Новый код (должен быть больше предыдущего!)
```

## 🛡️ Разрешения

### AndroidManifest.xml

Для работы автообновления требуются:

```xml
<!-- Интернет для проверки GitHub API -->
<uses-permission android:name="android.permission.INTERNET" />

<!-- Установка APK из неизвестных источников (Android 8+) -->
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />

<!-- Уведомления о прогрессе загрузки (Android 13+) -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

### FileProvider

Используется для безопасной передачи APK в системный инсталлятор:

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

## 🎨 UI элементы

### Кнопка ручной проверки

В секции "Интернет и обучение" добавлена кнопка **"🔄 Проверить обновление"**.

Пользователь может:
- Нажать кнопку для принудительной проверки
- Получить уведомление об отсутствии обновлений
- Увидеть ошибки при отсутствии интернета

### Автоматическая проверка

При запуске приложения:
- Проверка происходит в фоне (не блокирует UI)
- Если есть обновление — показывается диалог
- Если обновлений нет — ничего не показывается (silent mode)

## 🐛 Отладка

### Логи

Все события автообновления логируются с тегом `UpdateManager`:

```bash
adb logcat -s UpdateManager
```

Примеры логов:
```
D/UpdateManager: Проверка обновлений...
D/UpdateManager: Текущая версия: 32.0 (code: 320000)
D/UpdateManager: Последняя версия на GitHub: 33.0 (code: 330000)
D/UpdateManager: Найден APK: app-release.apk -> https://...
D/UpdateManager: Запуск загрузки: https://...
D/UpdateManager: Загрузка запущена, ID: 12345
```

### Частые проблемы

1. **"Не удалось проверить обновления"**
   - Проверьте интернет-соединение
   - Убедитесь, что GitHub API доступен (может блокироваться в некоторых сетях)
   - Проверьте правильность `GITHUB_OWNER` и `GITHUB_REPO` в `UpdateManager.kt`

2. **"URL APK не найден"**
   - Убедитесь, что к релизу прикреплён файл с расширением `.apk`
   - Проверьте, что файл загружен полностью (не повреждён)

3. **"Не удалось начать загрузку"**
   - Проверьте наличие свободного места на устройстве
   - Убедитесь, что разрешены загрузки через `DownloadManager`

4. **Установка не запускается**
   - На Android 8+ нужно разрешение "Установка из неизвестных источников"
   - Приложение автоматически открывает настройки для получения разрешения
   - После включения разрешения попробуйте снова

## 📱 Требования

- **minSdk**: 24 (Android 7.0)
- **targetSdk**: 34 (Android 14)
- **Internet**: требуется для проверки обновлений
- **Storage**: для скачивания APK (используется `DownloadManager`)

## 🔐 Безопасность

- Проверка обновлений происходит только через HTTPS (GitHub API)
- APK скачивается только из официального репозитория
- FileProvider используется для безопасной передачи файлов между приложениями
- Пользователь всегда явно подтверждает установку обновления

## 📊 Мониторинг

GitHub автоматически отслеживает статистику релизов:
- Количество скачиваний
- Дата последнего скачивания
- Размер релиза

Статистика доступна на странице: https://github.com/zigorminsk-debug/Weriter/releases

## 🚀 CI/CD интеграция (опционально)

Можно настроить автоматическую публикацию релизов через GitHub Actions:

```yaml
# .github/workflows/release.yml
name: Release

on:
  push:
    tags:
      - 'v*'

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Build APK
        run: ./gradlew assembleRelease
      - name: Create Release
        uses: softprops/action-gh-release@v1
        with:
          files: zi-writer/app/build/outputs/apk/release/app-release.apk
```

## 📚 Дополнительные ресурсы

- [GitHub Releases API](https://docs.github.com/en/rest/releases)
- [Android DownloadManager](https://developer.android.com/reference/android/app/DownloadManager)
- [FileProvider Guide](https://developer.android.com/reference/androidx/core/content/FileProvider)
- [Request Install Packages](https://developer.android.com/reference/android/Manifest.permission#REQUEST_INSTALL_PACKAGES)


## 🌐 Источники проверки обновлений (v33+)

Приложение проверяет обновление по 4 источникам по очереди — если один недоступен
(например, провайдер блокирует api.github.com), используется следующий:

1. `api.github.com/repos/.../releases/latest` — официальный API (pre-release НЕ учитываются)
2. `api.github.com/repos/.../releases?per_page=5` — список релизов (видит и pre-release)
3. `raw.githubusercontent.com/.../main/RELEASE_INFO.json` — зеркало в репозитории
4. `raw.githubusercontent.com/.../master/RELEASE_INFO.json` — то же, для старых репозиториев

Файл `RELEASE_INFO.json` — это страховка на случай блокировки API GitHub:
он содержит номер версии, описание и прямую ссылку на APK. CI обновляет его
автоматически при каждом релизе (шаг «Обновление RELEASE_INFO.json» в workflow),
вручную править его не нужно.

Приложение показывает понятную причину сбоя проверки: блокировка провайдером
(рекомендация VPN), отсутствие релизов или таймаут — вместо общего «нет интернета».
