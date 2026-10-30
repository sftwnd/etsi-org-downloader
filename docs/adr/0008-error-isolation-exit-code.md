# ADR-0008: Error isolation and the exit status

- Status: Accepted
- Date: 2026-10-30
- Amended: 2026-10-30 — the exit status 3 of a run stopped by another run (#62)
- Russian version: [ADR-0008](ru/0008-error-isolation-exit-code.md)

## Context

`TextHtmlProcessor.process` and `FileSaveProcessor` caught only `IOException` (#13). Any other exception — a too long
number, month 13, a space in a link, a non-HTTP scheme — completed the future of a child resource exceptionally;
`allOf` dragged it up to the root, and `join()` in `main` threw `CompletionException` without a summary.
Errors that were only written to the log gave exit status 0, so cron or CI did not see the failure.

## Decision

- `Summary` ([ADR-0007](0007-summary-result.md)) counts failures as well: files that could not be saved, listings that
  could not be fetched or parsed, and listing entries that could not be parsed.
- Every listing entry is parsed separately: an exception while parsing it is a warning with the escaped text of the
  entry and one failure; the other entries are processed.
- The future of every child resource is completed with `exceptionally`: any exception of a processor is an error in
  the log and `Summary.FAILED` of that resource.
- A failure to fetch or parse a listing as a whole is `Summary.FAILED` of the folder.
- `Main.run()` returns the exit status, and `main` passes it to `System.exit`:
  `0` — no failures, `1` — some resources have not been processed, `2` — invalid configuration,
  `3` — another run mirrors to the destination folder ([ADR-0014](0014-run-lock.md)).

## Rationale

- A failure of one resource is an expected situation when crawling hundreds of thousands of resources; it must not
  cancel the result of the others.
- Isolation at the boundary of a child future covers any processor error, including future ones, not only those listed
  in the task.
- Failures in the summary and the exit status make a partial failure visible to a person and to an automated run;
  different statuses distinguish a problem of the site or the network from an error in the parameters.
- The logic in `run()` without `System.exit` allows testing the exit status.

## Consequences

- A run always ends with the summary `Files downloaded: N, up to date: M, failed: K`.
- A script or a scheduler may repeat a run on status `1`; the files downloaded in the failed run are not downloaded
  again.
- The decision on the removal of expired versions can rely on the number of failures in the subtree of the actual
  version (#8).
