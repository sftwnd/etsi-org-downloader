# Scheduled runs

A mirror is kept up to date by running the downloader regularly, e.g. once a day: a run downloads only the files that
are new or changed on the site and removes the expired versions ([ReadMe](../ReadMe.md)). A dry run
(`ETSIORG_DRY_RUN=true`) shows what the next run would do.

## Exit statuses

A scheduler sees the result of a run in its exit status:

| Status | Meaning                                       | Reaction                                                                    |
|--------|-----------------------------------------------|-----------------------------------------------------------------------------|
| `0`    | no failures                                   | none                                                                        |
| `1`    | some resources have failed                    | none at once: the next run repeats them; a status 1 run after run is worth a look at the log |
| `2`    | invalid configuration                         | fix the settings or the destination folder: a repeated run fails the same way |
| `3`    | out of memory                                 | raise the limit: `ETSIORG_MEMORY` in Docker, `-Xmx` with `run.sh`           |
| `4`    | another run mirrors to the destination folder | none: nothing has been done, and the run in progress does the work         |

## cron

With `run.sh`, after the jar has been built once (`./mvnw package`); `JAVA_HOME` points to a JDK 11, since cron
has a minimal environment:

```cron
15 3 * * * cd /opt/etsi-org-downloader && JAVA_HOME=/usr/lib/jvm/java-11 NO_COLOR=1 ./run.sh -Ddest=/srv/etsi > /var/log/etsiorg/mirror.log 2>&1
```

With Docker, after the image has been built once (`docker compose build`); `docker compose run --rm` starts the service
and removes its container when the run ends:

```cron
15 3 * * * cd /opt/etsi-org-downloader && docker compose run --rm etsiorg > /var/log/etsiorg/mirror.log 2>&1
```

`ETSIORG_MIRROR` in `.env` of the project folder sets the host folder of the mirror for both the manual and
the scheduled runs.

## systemd timer

A service of the type `oneshot` runs the downloader, a timer starts it daily. systemd does not start a service that
is still running, and `SuccessExitStatus=4` does not count a run stopped by another run, e.g. a manual one, as a failure.
The log goes to the journal: `journalctl -u etsiorg-mirror`.

`/etc/systemd/system/etsiorg-mirror.service`:

```ini
[Unit]
Description=Mirror of www.etsi.org/deliver
Wants=network-online.target
After=network-online.target docker.service

[Service]
Type=oneshot
WorkingDirectory=/opt/etsi-org-downloader
ExecStart=/usr/bin/docker compose run --rm etsiorg
SuccessExitStatus=4
```

`/etc/systemd/system/etsiorg-mirror.timer`:

```ini
[Unit]
Description=Daily mirror of www.etsi.org/deliver

[Timer]
OnCalendar=*-*-* 03:15:00
RandomizedDelaySec=30m
Persistent=true

[Install]
WantedBy=timers.target
```

```console
sudo systemctl daemon-reload
sudo systemctl enable --now etsiorg-mirror.timer
```

## Kubernetes CronJob

The image is pushed to a registry the cluster pulls from, e.g.
`docker tag sftwnd/etsiorg-downloader registry.example.org/etsiorg-downloader:1.3 && docker push …`, and the mirror is
a persistent volume claim. `concurrencyPolicy: Forbid` does not start a run while the previous one is running;
the pod failure policy (Kubernetes 1.26 or later) does not repeat a run that ended with the status 2 or 3, since
the repetition would fail the same way, and `backoffLimit` repeats a run with failed resources once. The container
runs as the user of the image with the same limits as in `docker-compose.yml`; the heap is 75 % of the memory limit.

```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: etsiorg-mirror
spec:
  schedule: "15 3 * * *"
  concurrencyPolicy: Forbid
  jobTemplate:
    spec:
      backoffLimit: 1
      podFailurePolicy:
        rules:
          - action: FailJob
            onExitCodes:
              containerName: etsiorg
              operator: In
              values: [ 2, 3 ]
      template:
        spec:
          restartPolicy: Never
          securityContext:
            runAsUser: 65532
            runAsGroup: 65532
            fsGroup: 65532
          containers:
            - name: etsiorg
              image: registry.example.org/etsiorg-downloader:1.3
              env:
                - name: ETSIORG_URI
                  value: https://www.etsi.org/deliver/
              resources:
                limits:
                  cpu: "2"
                  memory: 1Gi
              securityContext:
                allowPrivilegeEscalation: false
                readOnlyRootFilesystem: true
              volumeMounts:
                - name: mirror
                  mountPath: /data
          volumes:
            - name: mirror
              persistentVolumeClaim:
                claimName: etsiorg-mirror
```
