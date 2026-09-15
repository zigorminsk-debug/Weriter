# Сборка APK и релизы

## Автоматическая сборка (GitHub Actions)

В репозитории два workflow:

| Workflow | Когда запускается | Что делает |
|---|---|---|
| `.github/workflows/android-release.yml` | публикация релиза на GitHub, пуш тега `v*`, вручную | собирает **подписанный release APK** и прикрепляет его к релизу |
| `.github/workflows/android-ci.yml` | push / PR в `main` (если менялся `zi-writer/**`) | проверочная сборка `assembleDebug`, чтобы ловить ошибки до релиза |

### Как выпустить релиз

**Вариант A — через интерфейс GitHub**

1. Releases → *Draft a new release*.
2. В поле *Tag* ввести `v32` (или `v32.1`) → *Create new tag on publish*.
3. Нажать *Publish release*.
4. Через ~5–8 минут во вкладке Actions завершится сборка, и к релизу
   будет прикреплён файл `ZI-Writer-v32.0-release.apk` + `.sha256`.

**Вариант B — через git**

```bash
git tag v32
git push origin v32
```

Релиз будет создан автоматически с release notes из коммитов.

### Версия приложения

Версия берётся из тега:

| Тег | versionName | versionCode |
|---|---|---|
| `v32` | `32.0` | `3200` |
| `v32.1` | `32.1` | `3201` |
| `v33` | `33.0` | `3300` |

`versionCode = MAJOR*100 + MINOR`. Он всегда растёт при росте тега, и любой
новый релиз (≥ 3200) больше кодов старых APK (≤ 31), поэтому обновление
устанавливается поверх без удаления.

### Секреты для подписи (настроить один раз)

Settings → Secrets and variables → Actions → *New repository secret*:

| Секрет | Значение |
|---|---|
| `KEYSTORE_BASE64` | `base64 -w0 zi-writer/zi-writer-release.jks` |
| `KEYSTORE_PASSWORD` | пароль keystore |
| `KEY_ALIAS` | `ziwriter` |
| `KEY_PASSWORD` | пароль ключа |

Если секреты не заданы, workflow предупредит и подпишет APK keystore'ом
из репозитория (`zi-writer/zi-writer-release.jks`) с паролями по умолчанию из
`app/build.gradle`. **Это небезопасно** — ключ и пароль публичны, любой может
выпустить «обновление» вашего приложения. После настройки секретов
удалите `.jks` из репозитория:

```bash
git rm zi-writer-release.jks zi-writer/zi-writer-release.jks
echo "*.jks" >> .gitignore
```

## Локальная сборка

```bash
cd zi-writer
./gradlew assembleRelease      # -> app/build/outputs/apk/release/ZI-Writer-v31.0-release.apk
./gradlew assembleDebug
```

Нужны JDK 17 и Android SDK (`compileSdk 34`). Путь к SDK — в
`zi-writer/local.properties` (файл в `.gitignore`, создаётся Android Studio).
