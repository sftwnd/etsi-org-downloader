# ADR-0011: The downloader is a named module (JPMS)

- Status: Accepted
- Date: 2026-10-30
- Russian version: [ADR-0011](ru/0011-jpms-module.md)

## Context

The downloader had no module descriptor and ran on the class path only (#52), although its runtime dependencies are
modules: `org.slf4j`, `ch.qos.logback.classic` and `ch.qos.logback.core`. The fat jar `jar-with-dependencies` unpacked
the jars of the dependencies into one together with their module descriptors: `jar --describe-module` took it for the
module `ch.qos.logback.classic@1.5.38`.

## Decision

- `module-info.java` declares the module `com.github.sftwnd.etsiorg`: `requires org.slf4j`; Lombok and the SpotBugs
  annotations, which are needed for compilation only, are `requires static`; the module exports nothing.
- The module does not require logback: `org.slf4j` uses `SLF4JServiceProvider`, and logback-classic provides it, so on
  the module path logback is resolved by service binding.
- The jar of the project is a modular jar: `maven-jar-plugin` sets the main class in the module descriptor, and
  `maven-dependency-plugin` copies the runtime modules to `target/lib`. Start:
  `java --module-path target/etsiorg-downloader-<version>.jar:target/lib --module com.github.sftwnd.etsiorg`.
- The fat jar stays the jar for the class path (`run.sh`, Docker): an assembly descriptor of its own repeats
  `jar-with-dependencies` without `module-info.class`.
- The tests run on the module path, patched into the module; what they use beyond the descriptor is given to them only:
  `jdk.httpserver` and `java.xml` to the compiler, the reads of `jdk.httpserver` and of logback to Surefire.
- `ModulePathIT` (Failsafe, `./mvnw verify`) checks the packaged jars: the descriptor and its main class, the binding of
  logback, a start on the module path and on the class path, the absence of module descriptors in the fat jar.

## Rationale

- The descriptor states the dependencies explicitly and is checked by the compiler: a missing `requires` fails the
  build, not a start.
- Binding logback as a service keeps the code on the SLF4J API: the logging implementation can be replaced without
  changing the module.
- `requires static` keeps what is needed for compilation only out of the runtime: neither `target/lib` nor the fat jar
  contains Lombok or the annotations.
- The fat jar cannot be a correct module: it mixes the packages of several modules. Without descriptors it is a plain
  jar on the class path and an automatic module, not logback, on the module path.
- The tests inside the module check the same encapsulation the downloader runs with; the test-only flags do not widen
  the descriptor.

## Consequences

- There are two ways to start: the fat jar on the class path (`run.sh`, Docker) and the module on the module path.
- A new dependency of the code needs a `requires` in `module-info.java`; a new dependency of the tests beyond the module
  may need an `--add-reads` or `--add-modules` in `pom.xml`.
- `./mvnw verify` takes a few seconds more: it starts the downloader twice.
