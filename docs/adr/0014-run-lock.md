# ADR-0014: One run at a time per destination folder

- Status: Accepted
- Date: 2026-10-30
- Amended: 2026-10-30 — the exit status 4 instead of 3, which is the status of the JVM on OutOfMemoryError (#58)
- Russian version: [ADR-0014](ru/0014-run-lock.md)

## Context

Runs on a schedule may overlap, and `docker compose up` and `docker compose run` may start at the same time (#62).
The locks of the `.part` files keep every file consistent ([ADR-0005](0005-part-file-download.md)), but both runs crawl
the whole tree: the load on the site doubles, and the second run only competes with the first one for the same files.

## Decision

- A run holds an exclusive lock of the operating system (`FileChannel.tryLock`) on the file `.etsiorg-downloader.lock`
  in the destination folder from the start of the crawl to its end; `RunLock` acquires and releases it.
- The lock is taken after the destination folder has been prepared ([ADR-0001](0001-destination-local-paths.md)) and
  before the first request. If another run holds it, the run logs `Another run is mirroring to '<dest>'` and ends with
  the exit status 4 ([ADR-0008](0008-error-isolation-exit-code.md)); 3 is the status of the JVM on an
  `OutOfMemoryError` ([ADR-0016](0016-resource-limits.md)).
- The lock file is not removed: removing a held file would let a third run lock a new file next to a running run.
- A process does not open a lock file it already holds: closing another channel of the file would release the lock
  of the whole process on POSIX systems.
- `Destination` maps no resource of the site to the lock file, whatever the case of its name.

## Rationale

- The operating system releases the lock when the process exits however it exits, `kill -9` and a stopped container
  included: a stale lock needs neither a timeout nor a manual removal, unlike a file that marks a run by its existence.
- Containers that mount the same host folder share the locks of the kernel, so `docker compose up` and
  `docker compose run` exclude each other, as do two starts of `run.sh`.
- A status of its own lets a scheduler tell an overlap from a failure: 4 means that nothing has been done.
- A resource that replaced the lock file would break the exclusion, so its name is reserved like the unsafe paths.

## Consequences

- Two runs do not mirror to the same folder at once; the second one ends at once without requests.
- The empty file `.etsiorg-downloader.lock` stays in the destination folder.
- The exclusion is as good as the locks of the file system. A network file system may not pass them between hosts,
  and Docker Desktop on macOS does not pass them between the host and a container: a start of `run.sh` and a run
  of the container on the same folder do not exclude each other (checked), while two containers or two host processes
  do.
