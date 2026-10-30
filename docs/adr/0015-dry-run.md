# ADR-0015: Dry run — the crawl of a run with a plan instead of downloads and removals

- Status: Accepted
- Date: 2026-10-30
- Russian version: [ADR-0015](ru/0015-dry-run.md)

## Context

A run without settings mirrors the whole `/deliver/` (#64): neither the number nor the size of the files to download is
known before it starts, and the free space of the destination is not checked. Nor could one see what a run would do
with an existing mirror — which files it would download again and which expired versions it would remove — without
letting it do so.

## Decision

- The setting `dry-run` (`-Ddry-run=true`, `ETSIORG_DRY_RUN=true`, [ADR-0013](0013-settings-sources.md)) starts a dry
  run: the crawl of a run — the same link policy, version choice and pool — in which files and expired versions are
  planned instead of saved and removed:
  - `FileSaveProcessor.plan()` makes the decision of `process()` without its actions: the size and time of the file
    from the listing are compared with the local copy ([ADR-0006](0006-changed-file-detection.md)); the file is not
    requested, no folder is created, no time is reset;
  - `ExpiredRemover.plan()` reports the existing local copies of the expired versions instead of removing them.
- `Main` chooses the strategy: it gives `ComplexProcessorFactory` the processor factory of the files (saving or
  planning) and the handler of the expired versions, which returns a `Summary` ([ADR-0007](0007-summary-result.md)).
- A dry run neither prepares the destination folder ([ADR-0001](0001-destination-local-paths.md)) nor takes the lock
  of a run ([ADR-0014](0014-run-lock.md)): it writes nothing. At the end it reports the files to download with their
  size, the up to date files, the expired versions to remove, the failures and the usable space of the file system
  of the destination folder or of its nearest existing parent, with a warning if the files do not fit.
- The exit statuses are those of a run: 0, 1 if a listing has failed, 2 for an invalid configuration.

## Rationale

- The plan is made by the code of the run: the version choice and the decision "up to date or to download" exist once,
  so the plan cannot differ from what the run does (DRY).
- The requests of a dry run are the listings only, a small part of the traffic of a run: no document is requested.
- Writing nothing, a dry run works with a read-only mirror and next to a running run, e.g. to check the state of
  a mirror.
- The processors keep one task each, and the choice between saving and planning is made once where the run is
  assembled (SRP, DIP): `ComplexProcessorFactory` depends on a processor factory of the files, not on its classes.

## Consequences

- A dry run of the whole `/deliver/` still requests every listing of the tree and takes time accordingly.
- The comparison with the free space is an estimate: a file downloaded again replaces its local copy, so a run may need
  less space; a `.part` file of an interrupted download is not counted.
- The summary of a run shows the size of the downloaded files and the number of the removed expired versions as well.
