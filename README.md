# Vesence

Клиент-мод для Minecraft **26.3** (Fabric Loader, Fabric Loom).

Требования: **JDK 25**, интернет (Gradle сам скачает зависимости и Minecraft).

## Как запустить (IntelliJ IDEA)

1. Скачай проект: кнопка **Code → Download ZIP** (и распакуй) или `git clone`.
2. Открой папку проекта в IDEA: **File → Open…** — дождись окончания Gradle-синхронизации.
3. Проверь, что в **File → Project Structure → SDK** стоит JDK 25.
4. Запусти клиент: Gradle-панель → `vesence → Tasks → fabric → runClient`
   (или из корня проекта: `./gradlew runClient`, на Windows — `run-client.bat`).

## Сборка

```bash
./gradlew build
```

Готовый jar мода появится в `build/libs/`.

## Структура

```
src/main/java/vesence/             код мода (пакет vesence)
src/main/resources/assets/vesence/ ресурсы: шрифты, шейдеры, кадры видео меню
src/main/resources/fabric.mod.json
gradle.properties                  версии Minecraft / loader / fabric-api
src/main/resources/vesence.accesswidener
```

## Примечания

- Ветка по умолчанию: `port-26.3`.
- Minecraft 26.x не обфусцирован — маппинги не нужны.
- `processResources` исключает `assets/vesence/videos/background.mp4`.
