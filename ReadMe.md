# ETSI.ORG Documents Loader

## Run

```console
./run.sh [JVM options]
```

The script builds the jar if it is missing and mirrors `https://www.etsi.org/deliver/` into `./deliver` of the current directory.
The options are passed to the JVM, e.g. another site folder and destination:

```console
./run.sh -Duri=https://www.etsi.org/deliver/etsi_ts/ -Ddest=/data/etsi
```

| Property   | Default                         | Meaning                                                              |
|------------|---------------------------------|----------------------------------------------------------------------|
| `-Duri`    | `https://www.etsi.org/deliver/` | Site folder to mirror; only links inside it are followed             |
| `-Ddest`   | `.`                             | Destination folder; files are stored under their path on the site    |
| `-Dthreads`| number of processors            | Threads that process listings and files, i.e. simultaneous requests  |
| `-Dzone`   | `UTC`                           | Time zone of the listing time; www.etsi.org lists it in UTC          |

The run ends with the numbers of downloaded, up to date and failed files and the exit status:
`0` — no failures, `1` — some resources have failed, `2` — invalid configuration.

## Start Download in Docker compose

```console
docker compose up --build
```

The image is built from the sources and runs the downloader as a non-root user (UID 10001) with a read-only file system;
only `./deliver` is mounted, the mirror is stored there. On Linux create the folder and make it writable for the user
before the first start, e.g. `mkdir -p deliver && sudo chown 10001 deliver`.

Properties are passed with `JDK_JAVA_OPTIONS`:

```console
docker compose run --rm -e JDK_JAVA_OPTIONS="-Duri=https://www.etsi.org/deliver/etsi_ts/ -Dthreads=4" etsiorg
```

## Remove Docker image

```console
docker rmi sftwnd/etsi-org:1.0
```
