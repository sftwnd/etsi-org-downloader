# ADR-0016: Bounded resources — limits of the container, the heap as their share, an exit on OutOfMemoryError

- Status: Accepted
- Date: 2026-10-30
- Russian version: [ADR-0016](ru/0016-resource-limits.md)

## Context

`docker-compose.yml` set no limits (#58): the JVM sized its heap by the memory of the Docker virtual machine — about
5.9 GiB, a quarter of 23.7 GB, on a developer machine — and the container could take all the processors. After
an `OutOfMemoryError` the JVM went on: the error became a failure of the resource whose task it hit
([ADR-0008](0008-error-isolation-exit-code.md)), while any other thread could have lost its work.

The memory of a run grows with the tree: the visited resources ([ADR-0002](0002-link-policy.md)) and the futures of
the tree are kept until the end. A dry run of a synthetic tree of 100 000 files needed at most 124 MB of heap after a
collection, of 200 000 files 246 MB, and failed with a heap of 64 MB and 128 MB respectively: about 1.2 KB per file.

## Decision

- `docker-compose.yml` limits the container: `mem_limit: ${ETSIORG_MEMORY:-1g}` and `cpus: ${ETSIORG_CPUS:-2}`.
- The image starts the JVM with `-XX:MaxRAMPercentage=75`: the heap is 75 % of the limit, the rest is left
  to the metaspace, the threads and the buffers of the JVM.
- The image and `run.sh` start the JVM with `-XX:+ExitOnOutOfMemoryError`: the first `OutOfMemoryError` ends
  the process with the status 3.
- The status 3 is left to the JVM; a run stopped by another run ([ADR-0014](0014-run-lock.md)) ends with the status 4.

## Rationale

- The limit follows the tree, not the machine: 1 GiB gives a heap of 768 MiB, about 600 000 files by the measurement,
  whatever the memory of the host.
- After an `OutOfMemoryError` no thread can be trusted to have finished its work, so ending the process is the only
  safe outcome; the next run resumes the interrupted downloads ([ADR-0005](0005-part-file-download.md)).
- A status of its own tells a scheduler that the limit has to be raised, not that the site or the network has failed.
  It is the status `-XX:+ExitOnOutOfMemoryError` uses (checked on JDK 11, 17, 21 and 25), so the downloader does not
  return it for anything else.
- The work of a run is mostly waiting for the network with 4 threads ([ADR-0004](0004-bounded-executor.md));
  the processor limit keeps a run from competing with the rest of the machine.

## Consequences

- A tree too large for the limit ends with the status 3; `ETSIORG_MEMORY` raises the limit, `ETSIORG_CPUS`
  changes the processors.
- `run.sh` sets no limit: the heap is the default of the JVM, a quarter of the memory of the machine; `-Xmx` sets
  another one.
- The number of files the default limit allows is an estimate from a synthetic tree: the memory per file depends
  on the length of its path.
