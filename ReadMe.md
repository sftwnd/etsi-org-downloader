# ETSI.ORG Documents Downloader

[![Build](https://github.com/sftwnd/etsi-org-downloader/actions/workflows/build.yml/badge.svg)](https://github.com/sftwnd/etsi-org-downloader/actions/workflows/build.yml)

A console tool that mirrors the document tree of ETSI, [www.etsi.org/deliver](https://www.etsi.org/deliver/),
or one of its folders, to a local folder.

- Files are stored under their path on the site, `<dest>/deliver/etsi_ts/...`, with the time from the listing.
- Of the version folders `NN.NN.NN_NN` only the latest one is mirrored. The local copies of the older versions are
  removed after the latest one has been processed without failures.
- A file is downloaded to `<name>.<listing time>.part` and replaces the local copy only when it is complete;
  an interrupted download is resumed with HTTP `Range`. A file whose size or listing time has changed on the site is
  downloaded again, an up to date file is not requested.
- Listings are treated as untrusted: only links inside the start folder of the same host are followed,
  and a local path cannot leave the destination folder.
- Requests have timeouts and retries; the number of simultaneous requests is bounded by the number of threads.

## Requirements

- JDK 11 to build the tool and to start it with `run.sh`: Maven 3.10.0 is downloaded by the Maven Wrapper, or
- Docker with Compose.

## Build and test

```console
./mvnw clean package
```

runs the tests and builds:

- `target/etsiorg-downloader-<version>-jar-with-dependencies.jar` — the fat jar for the class path;
- `target/etsiorg-downloader-<version>.jar` — the named module `com.github.sftwnd.etsiorg`, and `target/lib` — the modules
  it needs at runtime.

`./mvnw test` runs the tests only (on the module path); `./mvnw verify` also starts the packaged jars on the module path
and on the class path. Continuous integration ([build.yml](.github/workflows/build.yml)) runs `./mvnw verify` on JDK 11,
17, 21 and 25 for every push and pull request and builds the Docker image.

The Maven Wrapper (`mvnw`, `mvnw.cmd` on Windows) downloads the Maven version of the project once, checks the SHA-256
of its distribution and runs it. Run it in the project folder: `.mvn/maven.config` applies the project settings
`.mvn/settings.xml` (Maven Central only) instead of the user settings.

## Run

```console
./run.sh [JVM options]
```

The script builds the jar if it is missing or older than the files it is built from — `pom.xml`, `lombok.config`,
`src/main` and `src/assembly` — and mirrors `https://www.etsi.org/deliver/` to `./deliver` of the current directory.
It starts the jar with the JDK the Maven Wrapper builds it with: `$JAVA_HOME/bin/java`, or `java` from `PATH` if
`JAVA_HOME` is not set. The options are passed to the JVM, e.g. another site folder and destination:

```console
./run.sh -Duri=https://www.etsi.org/deliver/etsi_ts/ -Ddest=/data/etsi
```

| Property    | Default                         | Meaning                                                              |
|-------------|---------------------------------|----------------------------------------------------------------------|
| `-Duri`     | `https://www.etsi.org/deliver/` | Site folder to mirror; only links inside it are followed             |
| `-Ddest`    | `.`                             | Destination folder; files are stored under their path on the site    |
| `-Dthreads` | number of processors            | Threads that process listings and files, i.e. simultaneous requests  |
| `-Dzone`    | `UTC`                           | Time zone of the listing time; www.etsi.org lists it in UTC          |

The log is colored for a terminal. As [no-color.org](https://no-color.org) suggests, `NO_COLOR` set to a non-empty value
turns the colors off, e.g. for a log written to a file: `NO_COLOR=1 ./run.sh > mirror.log`.

The jars can also be started directly; the logging configuration is not packed into them and is passed explicitly.
The fat jar on the class path:

```console
java -Dlogback.configurationFile=src/main/resources/logback.xml -Duri=https://www.etsi.org/deliver/etsi_ts/ \
     -jar target/etsiorg-downloader-<version>-jar-with-dependencies.jar
```

The module on the module path; logback is found as the service provider of SLF4J:

```console
java -Dlogback.configurationFile=src/main/resources/logback.xml -Duri=https://www.etsi.org/deliver/etsi_ts/ \
     --module-path target/etsiorg-downloader-<version>.jar:target/lib --module com.github.sftwnd.etsiorg
```

A run can be interrupted at any moment: the next run resumes the unfinished downloads. It ends with the summary
`Files downloaded: N, up to date: M, failed: K` and the exit status:

| Status | Meaning                                                                                     |
|--------|---------------------------------------------------------------------------------------------|
| `0`    | no failures                                                                                 |
| `1`    | some resources have failed; a repeated run does not download the already downloaded files   |
| `2`    | invalid configuration                                                                       |

## Docker

```console
docker compose up --build
```

The image is built from the sources with `./mvnw` without the tests, which run in continuous integration, and runs
the downloader as a non-root user (UID 10001) with a read-only file system; only `./deliver` is mounted, and the mirror
is stored there. On Linux create the folder and make it writable for the user before the first start,
e.g. `mkdir -p deliver && sudo chown 10001 deliver`. The log of the container is not colored: the image sets
`NO_COLOR=1`. A run logs a line per file, so the log is rotated: at most five files of 10 MB (`logging`
in `docker-compose.yml`), and `docker logs` shows what they hold.

Properties are passed with `JDK_JAVA_OPTIONS`:

```console
docker compose run --rm -e JDK_JAVA_OPTIONS="-Duri=https://www.etsi.org/deliver/etsi_ts/ -Dthreads=4" etsiorg
```

To remove the image:

```console
docker rmi sftwnd/etsi-org:1.0
```

## Architecture

The architecture decisions are recorded in [docs/adr](docs/adr/README.md) in English and in Russian
([docs/adr/ru](docs/adr/ru/README.md)); [ADR-0004](docs/adr/0004-bounded-executor.md) contains the C4 component diagram.

## License

[BSD 3-Clause](LICENSE)
