# Архитектурные решения

Решения ведутся на двух языках: по-русски в этой папке и по-английски в [docs/adr](../README.md).
Новое решение получает обе версии с одним именем файла, версии ссылаются друг на друга,
и решение добавляется в оба оглавления.

| ADR | Решение | Версия на английском |
|---|---|---|
| [0001](0001-destination-local-paths.md) | Отображение ресурсов сайта в локальные пути через Destination | [0001](../0001-destination-local-paths.md) |
| [0002](0002-link-policy.md) | Политика ссылок обхода — LinkPolicy | [0002](../0002-link-policy.md) |
| [0003](0003-http-timeouts-retries.md) | HTTP-доступ — HttpURLConnection с таймаутами и повторами | [0003](../0003-http-timeouts-retries.md) |
| [0004](0004-bounded-executor.md) | Модель конкурентности — вся обработка ресурсов в одном ограниченном пуле | [0004](../0004-bounded-executor.md) |
| [0005](0005-part-file-download.md) | Модель загрузки файла — `.part` и атомарная замена | [0005](../0005-part-file-download.md) |
| [0006](0006-changed-file-detection.md) | Решение о перекачке — размер и время листинга, `.part` привязан к версии файла | [0006](../0006-changed-file-detection.md) |
| [0007](0007-summary-result.md) | Результат обработки — значение Summary вместо потока путей | [0007](../0007-summary-result.md) |
| [0008](0008-error-isolation-exit-code.md) | Изоляция ошибок и код завершения | [0008](../0008-error-isolation-exit-code.md) |
| [0009](0009-expire-after-actual-version.md) | Удаление устаревших версий после успешной обработки актуальной | [0009](../0009-expire-after-actual-version.md) |
| [0010](0010-maven-wrapper.md) | Сборка через Maven Wrapper с настройками проекта | [0010](../0010-maven-wrapper.md) |
