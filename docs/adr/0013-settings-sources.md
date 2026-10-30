# ADR-0013: Settings — a system property, otherwise an environment variable, otherwise the default

- Status: Accepted
- Date: 2026-10-30
- Russian version: [ADR-0013](ru/0013-settings-sources.md)

## Context

The downloader read its settings only from the system properties `-Duri`, `-Ddest`, `-Dthreads` and `-Dzone` (#61).
A container is configured with environment variables, so the image got the properties through `JDK_JAVA_OPTIONS`,
and the JVM printed `NOTE: Picked up JDK_JAVA_OPTIONS: …` to the error stream on every start; `docker-compose.yml`
could not pass a setting from its own environment. The command line arguments were ignored silently: a mistyped option
such as `--dest=/data` started a run with the default settings, and there was no help. `Main` read the properties
itself, so its tests changed the global system properties.

## Decision

- `Settings` is the value of the settings of a run: `uri`, `dest`, `threads` and `zone`.
  `Settings.of(properties, environment)` takes a setting from its system property, otherwise from the environment
  variable `ETSIORG_<NAME>`, otherwise it is the default; a value is trimmed, and a blank one is not set. An invalid
  value, an invalid destination path included, is an `IllegalArgumentException`, i.e. the exit status 2
  ([ADR-0008](0008-error-isolation-exit-code.md)).
- The names, meanings and defaults of the settings are the enum `Settings.Key`; the help is generated from it.
- `Main.run(args, properties, environment)` gets the sources as functions: `main` passes `System::getProperty` and
  `System::getenv`, the tests pass maps. `-h` or `--help` alone prints the usage, the settings and the exit statuses
  to the standard output with the status 0; any other argument is an invalid configuration with the status 2.
- `run.sh` passes `-h` and `--help` to the downloader and the other arguments to the JVM. The image sets
  `ETSIORG_DEST=/data` instead of `-Ddest=/data`, and `docker-compose.yml` passes `ETSIORG_URI`, `ETSIORG_THREADS`
  and `ETSIORG_ZONE` from its environment or `.env`.

## Rationale

- Containers, systemd units and CI are configured with environment variables; the system properties stay for the
  command line of the JVM and `run.sh`.
- A property overrides a variable: it is given for one start of the JVM, while a variable may be inherited from
  the environment unnoticed. logback looks up a property before a variable as well, e.g. `NO_COLOR`.
- A blank value means "not set" in both sources: `ETSIORG_THREADS=` in `.env` or `-Dzone=` gives the default,
  not an error.
- An unknown argument stops the run before it starts, so a mistyped option does not mirror the whole site with
  the default settings.
- One description of the settings serves the parsing and the help, and they cannot diverge (DRY). `Main` only wires
  the run (SRP), and the settings are tested without the global system properties and environment.

## Consequences

- `JDK_JAVA_OPTIONS` is not needed for the settings any more; it still passes options of the JVM.
- A new setting is a constant of `Settings.Key`, a field of `Settings` and its parsing; the help shows it as it is.
- The variables have the prefix `ETSIORG_` and do not collide with the environment of other programs.
