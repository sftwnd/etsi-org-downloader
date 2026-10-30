# ADR-0011: Загрузчик — именованный модуль (JPMS)

- Статус: Принято
- Дата: 2026-10-30
- Изменено: 2026-10-30 — образ запускает модуль в среде, собранной jlink (#60)
- Версия на английском: [ADR-0011](../0011-jpms-module.md)

## Контекст

У загрузчика не было дескриптора модуля, он запускался только на classpath (#52), хотя его зависимости времени
выполнения — модули: `org.slf4j`, `ch.qos.logback.classic` и `ch.qos.logback.core`. Fat jar `jar-with-dependencies`
распаковывал jar зависимостей в один вместе с их дескрипторами: `jar --describe-module` принимал его за модуль
`ch.qos.logback.classic@1.5.38`.

## Решение

- `module-info.java` объявляет модуль `com.github.sftwnd.etsiorg`: `requires org.slf4j`; Lombok и аннотации SpotBugs,
  нужные только для компиляции, — `requires static`; модуль ничего не экспортирует.
- Модуль не требует logback: `org.slf4j` использует `SLF4JServiceProvider`, а logback-classic его предоставляет,
  поэтому на module path logback подключается связыванием сервисов.
- Jar проекта — модульный: `maven-jar-plugin` записывает главный класс в дескриптор модуля, а `maven-dependency-plugin`
  копирует модули времени выполнения в `target/lib`. Запуск:
  `java --module-path target/etsiorg-downloader-<version>.jar:target/lib --module com.github.sftwnd.etsiorg`.
- Fat jar остаётся jar для classpath (`run.sh`): собственный дескриптор сборки повторяет `jar-with-dependencies`
  без `module-info.class`.
- Тесты выполняются на module path внутри модуля; то, что им нужно сверх дескриптора, выдаётся только им: компилятору —
  `jdk.httpserver` и `java.xml`, Surefire — чтение `jdk.httpserver` и logback.
- `ModulePathIT` (Failsafe, `./mvnw verify`) проверяет собранные jar: дескриптор и его главный класс, связывание
  logback, запуск на module path и на classpath, отсутствие дескрипторов модулей в fat jar.

## Обоснование

- Дескриптор явно перечисляет зависимости и проверяется компилятором: пропущенный `requires` ломает сборку, а не запуск.
- Связывание logback как сервиса держит код на API SLF4J: реализацию журнала можно заменить, не меняя модуль.
- `requires static` не переносит в среду выполнения то, что нужно только для компиляции: ни в `target/lib`, ни в fat jar
  нет Lombok и аннотаций.
- Fat jar не может быть корректным модулем: в нём смешаны пакеты нескольких модулей. Без дескрипторов он — обычный jar
  на classpath и автоматический модуль, а не logback, на module path.
- Тесты внутри модуля проверяют ту же инкапсуляцию, с которой работает загрузчик; флаги только для тестов не расширяют
  дескриптор.

## Последствия

- Есть два способа запуска: fat jar на classpath (`run.sh`) и модуль на module path; образ запускает модуль в среде,
  собранной jlink ([ADR-0017](0017-jlink-runtime-image.md)).
- Новая зависимость кода требует `requires` в `module-info.java`; новая зависимость тестов за пределами модуля может
  потребовать `--add-reads` или `--add-modules` в `pom.xml`.
- `./mvnw verify` идёт на несколько секунд дольше: он дважды запускает загрузчик.
