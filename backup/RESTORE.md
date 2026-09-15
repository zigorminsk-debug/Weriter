# ZI Writer Backup Restore - v23.0 LEARNED 55%

## Что в бэкапе:
- Исходники Kotlin: zi-writer/app/src/main/java/com/zakharevich/ziwriter/*.kt (14 файлов)
- Layout: activity_main.xml (чистый UI без v8 ULTRA PRO)
- Themes: themes.xml
- Gradle: build.gradle (versionCode 23)
- APK: ZI-Writer-v23.0-LEARNED-55.apk (4.7M) - финальная версия
- Fix scripts: backup/fix_history/ (22 файла - история всех исправлений)

## Последняя версия v23:
- Чистый UI: убран весь тех. мусор "v8.0 ULTRA PRO • 440+ шаблонов • TF-IDF"
- Flibusta: PRIMARY = http://flibusta.net/opds (работает в РБ без VPN)
- Статистика: показывает книги с flibusta.net, не 0
- Генерация: 8 вариантов вступления вместо 1, 55% использование изученного (85% для первых 2 абзацев), 42 фразы в generalPool
- Фикс названия: "Планета Тоби" вместо "Свет Планете Тоби"

## Как восстановить за 1 минуту:

### 1. JDK 17 (95M, 5 сек):
```bash
curl -L -o /tmp/jdk.tar.gz "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse?project=jdk"
rm -rf ~/jdk-17 && mkdir ~/jdk-17 && tar -xzf /tmp/jdk.tar.gz -C ~/jdk-17 --strip-components=1
```

### 2. Android SDK (20M):
```bash
# Скачать cmdline-tools и platform 34, или использовать готовый из backup если есть
```

### 3. Сборка:
```bash
cd ~/zi-writer
export JAVA_HOME=~/jdk-17
export ANDROID_HOME=~/android-sdk
./gradlew assembleRelease
# APK будет в app/build/outputs/apk/release/app-release.apk
```

## Дата бэкапа: 2026-09-11
## Автор: Zakharevich Igor +375293371412
## Размер workspace после очистки: 15MB (было 155MB) - укладывается в лимит 128MB
