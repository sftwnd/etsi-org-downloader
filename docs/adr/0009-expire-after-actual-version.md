# ADR-0009: Remove expired versions after the actual version has been processed successfully

- Status: Accepted
- Date: 2026-10-30
- Russian version: [ADR-0009](ru/0009-expire-after-actual-version.md)

## Context

Of the version folders `NN.NN.NN_NN` only the actual version is downloaded; the local copies of the others are removed.
`TextHtmlProcessor` passed the expired versions to `onExpires` right while parsing the listing, while the actual
version was processed later and asynchronously (#8). If its download failed (network, 5xx, timeout), neither the old
nor the new version remained locally.

## Decision

- Parsing a listing only determines the actual version and the list of expired ones (strictly older by version
  number, #14).
- The expired versions are passed to `onExpires` in the continuation of the future of the actual version and only if
  its subtree has no failures (`Summary.getFailed() == 0`, [ADR-0008](0008-error-isolation-exit-code.md)).
- If the actual version has completed with failures or has not been processed in this listing (already visited),
  the expired versions stay, and a warning is written to the log.
- An exception during the removal is an error in the log and one failure in the summary.

## Rationale

- The user of the mirror always has at least one complete version of a document: the old one is removed only after
  the new one has been downloaded completely or was already up to date.
- The decision is made by the summary of the subtree ([ADR-0007](0007-summary-result.md),
  [ADR-0008](0008-error-isolation-exit-code.md)) without shared mutable state and without waiting in the pool threads:
  the removal is a continuation of the future of the actual version.

## Consequences

- After a failed run both the old and the new (partial) version may stay on disk for a while; the next successful run
  removes the old one.
- Expired versions are removed in the pool thread that has completed the actual version, later than before.
