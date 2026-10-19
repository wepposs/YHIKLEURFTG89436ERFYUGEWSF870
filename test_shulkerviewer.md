# Проверка ShulkerViewer

## Что сделано:
1. ✅ Создан файл: `src/main/java/vesence/module/impl/visuals/ShulkerViewer.java`
2. ✅ Создан миксин: `src/main/java/vesence/mixin/HandledScreenShulkerViewerMixin.java`
3. ✅ Добавлен в ModuleManager: строка `this.module.add(new vesence.module.impl.visuals.ShulkerViewer());`
4. ✅ Удалены папки figura: `build\resources\main\figura`, `run\figura`, `src\main\resources\figura`
5. ✅ Исправлена ошибка DyeColor: заменен на простую карту цветов

## Как проверить работу:
1. Запустить сборку: `./gradlew build`
2. Запустить клиент: `./gradlew runClient`
3. В игре:
   - Найти шалкер в мире или в инвентаре
   - Навести на него курсор
   - **Зажать ALT** (левый ALT)
   - Должно появиться превью содержимого

## Возможные проблемы:
1. **Модуль не появляется в GUI** - проверьте категорию VISUALS в Click GUI
2. **ALT не работает** - попробуйте изменить бинд в настройках модуля
3. **Текстура не загружается** - проверьте путь: `assets/vesence/textures/shulkerviewer/shulker_box_tooltip.png`
4. **Содержимое не показывается** - возможно, шалкер пустой или система компонентов не работает

## Если не работает:
1. Проверьте логи клиента на ошибки
2. Убедитесь, что модуль включен (галочка в GUI)
3. Проверьте настройки модуля (включен ли "Отображать в мире")