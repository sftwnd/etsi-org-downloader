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

## Remove Docker image

```console
docker rmi sftwnd/etsi-org:1.0
```

## Start Download in Docker compose

```console
docker compose up
```
