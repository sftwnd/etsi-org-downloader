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
17, 21 and 25 for every push and pull request and builds the Docker image. The base images of the image are pinned by
digest; Dependabot proposes their updates, as well as those of the Maven dependencies and plugins and of the GitHub
Actions.

The Maven Wrapper (`mvnw`, `mvnw.cmd` on Windows) downloads the Maven version of the project once, checks the SHA-256
of its distribution and runs it. Run it in the project folder: `.mvn/maven.config` applies the project settings
`.mvn/settings.xml` (Maven Central only) instead of the user settings.

## Run

```console
./run.sh [JVM options] [--help]
```

The script builds the jar if it is missing or older than the files it is built from — `pom.xml`, `lombok.config`,
`src/main` and `src/assembly` — and mirrors `https://www.etsi.org/deliver/` to `./deliver` of the current directory.
It starts the jar with the JDK the Maven Wrapper builds it with: `$JAVA_HOME/bin/java`, or `java` from `PATH` if
`JAVA_HOME` is not set. The options are passed to the JVM; `-h` or `--help` prints the settings.

A setting is a system property or an environment variable; the property overrides the variable, and a blank value
is not set. E.g. another site folder and destination:

```console
./run.sh -Duri=https://www.etsi.org/deliver/etsi_ts/ -Ddest=/data/etsi
ETSIORG_URI=https://www.etsi.org/deliver/etsi_ts/ ETSIORG_DEST=/data/etsi ./run.sh
```

| Property    | Environment variable | Default                         | Meaning                                                             |
|-------------|----------------------|---------------------------------|---------------------------------------------------------------------|
| `-Duri`     | `ETSIORG_URI`        | `https://www.etsi.org/deliver/` | Site folder to mirror; only links inside it are followed            |
| `-Ddest`    | `ETSIORG_DEST`       | `.`                             | Destination folder; files are stored under their path on the site   |
| `-Dthreads` | `ETSIORG_THREADS`    | `4`                             | Threads that process listings and files, i.e. simultaneous requests |
| `-Dzone`    | `ETSIORG_ZONE`       | `UTC`                           | Time zone of the listing time; www.etsi.org lists it in UTC         |
| `-Ddry-run` | `ETSIORG_DRY_RUN`    | `false`                         | `true`: report what a run would download and remove, change nothing |

A dry run walks the listings and reports the files to download with their size, the expired versions to remove and
the free space of the destination folder; it downloads, removes and creates nothing, so it needs no write access and
runs next to a running run:

```console
./run.sh -Ddry-run=true
```

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

A run can be interrupted at any moment: the next run resumes the unfinished downloads. One run at a time mirrors to
a destination folder: it holds a lock of the operating system on the file `.etsiorg-downloader.lock` there, which stays
in the folder. A run ends with the summary `Files downloaded: N (size), up to date: M, expired versions removed: R,
failed: K` and the exit status:

| Status | Meaning                                                                                                                                                             |
|--------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `0`    | no failures                                                                                                                                                         |
| `1`    | some resources have failed; a repeated run does not download the already downloaded files                                                                           |
| `2`    | invalid configuration, found before the first request: an invalid setting, an argument other than `-h` and `--help`, or a destination folder that cannot be written |
| `3`    | out of memory: the JVM ends the run on `OutOfMemoryError` (`-XX:+ExitOnOutOfMemoryError` of `run.sh` and the image)                                                 |
| `4`    | another run mirrors to the destination folder; nothing has been done                                                                                                |

## Docker

```console
docker compose up --build
```

The image is built from the sources with `./mvnw` without the tests, which run in continuous integration. It holds a
Java runtime linked by `jlink` from the modules of the downloader on `gcr.io/distroless/base-debian12`, about 60 MB, and
runs the module as the user `nonroot` (UID 65532) with a read-only file system. Only the destination folder is mounted,
at `/data`: `./mirror` of the project folder, or the host folder `ETSIORG_MIRROR` names in the environment or in `.env`,
e.g. `ETSIORG_MIRROR=/srv/etsi docker compose up`. The files are stored under their path on the site,
`mirror/deliver/...`. On Linux create the folder and make it writable for the user before the first start, e.g.
`mkdir -p mirror && sudo chown 65532 mirror`; otherwise the run stops at once with the status 2. A mirror made by an
earlier version in `./deliver` of the project folder is moved once: `mkdir -p mirror && mv deliver mirror/`. The log of
the container is not colored: the image sets `NO_COLOR=1`. A run logs a line per file, so the log is rotated: at most
five files of 10 MB (`logging` in `docker-compose.yml`), and `docker logs` shows what they hold.

The container is limited to 1 GiB of memory and 2 processors; `ETSIORG_MEMORY` and `ETSIORG_CPUS` change the limits,
e.g. `ETSIORG_MEMORY=2g docker compose up`. The heap is 75 % of the memory limit. A run keeps about 1.2 KB of heap per
file of the tree (measured on a synthetic tree), so 1 GiB is enough for about 600 000 files; a tree that does not fit
ends with the status 3.

The settings are environment variables: the image sets `ETSIORG_DEST=/data`, and `docker-compose.yml` passes
`ETSIORG_URI`, `ETSIORG_THREADS`, `ETSIORG_ZONE` and `ETSIORG_DRY_RUN` from the environment of docker compose
or from `.env`:

```console
ETSIORG_URI=https://www.etsi.org/deliver/etsi_ts/ ETSIORG_THREADS=2 docker compose up
docker compose run --rm -e ETSIORG_URI=https://www.etsi.org/deliver/etsi_ts/ etsiorg
docker compose run --rm -e ETSIORG_DRY_RUN=true etsiorg
docker compose run --rm etsiorg --help
```

To remove the image:

```console
docker rmi sftwnd/etsiorg-downloader
```

The image `sftwnd/etsiorg-downloader` is built from the sources at hand, so it has no version tag: the downloader logs
its version at the start and prints it with `--help`.

## Scheduled runs

A mirror is kept up to date by a regular run. [docs/scheduling.md](docs/scheduling.md) has the examples for cron,
a systemd timer and a Kubernetes CronJob and the reaction to every exit status.

## Architecture

The architecture decisions are recorded in [docs/adr](docs/adr/README.md) in English and in Russian
([docs/adr/ru](docs/adr/ru/README.md)); [ADR-0004](docs/adr/0004-bounded-executor.md) contains the C4 component diagram,
[ADR-0017](docs/adr/0017-jlink-runtime-image.md) the deployment diagram.

## License

[BSD 3-Clause](LICENSE)
