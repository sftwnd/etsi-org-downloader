# ADR-0007: Processing result — a Summary value instead of a stream of paths

- Status: Accepted
- Date: 2026-10-30
- Amended: 2026-10-30 — the size of the downloaded files and the number of the removed expired versions (#64)
- Russian version: [ADR-0007](ru/0007-summary-result.md)

## Context

The processors returned `CompletableFuture<Stream<Path>>` — a stream of the paths of all the files of the subtree (#22):

- `FileSaveProcessor` returned the path of a skipped, already up to date file as well, so the summary "There are
  N files has been loaded" counted all the files of the mirror, not the downloaded ones;
- `Main` collected and sorted the paths of the whole tree only to print their number: memory grew with the number of
  files of the mirror, and these are hundreds of thousands of paths.

## Decision

- The result of processing is an immutable `Summary` value with the numbers of downloaded and up to date files;
  `Processor<CompletableFuture<Summary>>` for all the processors.
- `FileSaveProcessor` returns `Summary.downloaded(bytes)`, `Summary.UP_TO_DATE` or `Summary.EMPTY`
  (the file is not saved: an error, or the file is held by another task); `EmptyProcessor` returns `Summary.EMPTY`.
- `Summary` also has the size of the downloaded files and the number of the expired versions whose local copies have
  been removed: the handler of the expired versions returns `Summary.removed(count)`. A dry run counts what a run would
  download and remove ([ADR-0015](0015-dry-run.md)).
- `TextHtmlProcessor` adds up the results of its children (`Summary::plus`) when all of them are completed.
- `Main` prints the numbers of downloaded and up to date files.

## Rationale

- A value of a fixed size instead of a list of paths: memory does not depend on the size of the tree, and the addition
  is associative and needs no synchronization — the results are collected by composing futures without shared mutable
  state.
- The summary distinguishes downloaded and already up to date files and answers the question of what the run has done.
- `Summary` is the natural place to account for failures and the exit status (#13) and to decide on the removal of
  expired versions by the result of the subtree of the actual version (#8).

## Consequences

- The paths of downloaded files are no longer returned: they are visible in the log (`File ... has been saved`).
- The tests check the result as a value, and the processed resources through a test factory.
