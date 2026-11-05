# Лист

Просмотрщик файлов для Android. Всё открывается прямо в приложении и работает без интернета.

**Скачать APK:** [List.apk](../../releases/latest/download/List.apk)

Что открывает: PDF; Word (docx, doc, odt, rtf); Excel (xlsx, xls, ods, csv); PowerPoint (pptx, ppt, odp);
HTML, Markdown, JSON, текст и код; книги EPUB и FB2; архивы ZIP, RAR, 7z, TAR, GZ; картинки, GIF, SVG; аудио и видео.

Старые форматы (doc, ppt, odt, odp, rtf) открываются через встроенный движок ONLYOFFICE (x2t, WebAssembly).

## Как это собирается
Сборка идёт на GitHub Actions при каждом `git push`: готовый APK автоматически попадает в раздел Releases.
Приложение подписано постоянным ключом, поэтому новые версии ставятся поверх старых.

## Лицензия
AGPL-3.0 (этого требует встроенный движок ONLYOFFICE). Сторонние компоненты перечислены в `THIRD_PARTY.txt`.
