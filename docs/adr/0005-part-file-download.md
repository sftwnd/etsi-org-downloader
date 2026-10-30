# ADR-0005: File download model — `.part` and atomic replacement

- Status: Accepted
- Date: 2026-10-30
- Russian version: [ADR-0005](ru/0005-part-file-download.md)

## Context

`FileSaveProcessor` wrote a file directly under its final name and deleted it on any `IOException` (#9):

- if the time of an already downloaded file could not be set (a file of another user, a file system without the
  attribute), the complete file was deleted, and the next run downloaded and deleted it again;
- when the network broke, the partially downloaded file was deleted, so the resume from #1 worked only after the
  process was killed;
- a partial file under the final name differed from a complete one only by its size, and any existing smaller file
  was treated as partial and appended to — including a foreign file (together with #4 and #5 this allowed writing
  to `run.sh`).

## Decision

- A file is downloaded to a sibling file `<name>.part`; the final name appears only for a complete file — after the
  time from the listing is set, `.part` is renamed to it with `Files.move(..., ATOMIC_MOVE)`
  (`REPLACE_EXISTING` where an atomic rename is not supported).
- A resume continues only a `.part`; a `.part` longer than the expected file is removed, and the download starts over.
- An existing file is never appended to: a file of the expected size is up to date, a file of another size is
  downloaded again to `.part` and replaced as a whole.
- On a download error `.part` is kept for a resume, and the existing file is not touched. A failure to set the time
  is a warning, not a removal.
- A path occupied by something other than a regular file (a folder, a symbolic link) is an error; the path is not
  changed.

## Rationale

- A complete file and an unfinished download differ by name, not by indirect signs; after a failure at any step the
  final name holds either the previous file or the new one as a whole.
- A rename within one folder is atomic on POSIX systems, so a reader of the mirror never sees a half-written file,
  and the file time is set before the final name appears.
- The program removes and overwrites only what it has created itself: `.part` and the file it replaces as a whole.
- A resume after a network error and after a killed process works the same way — through `.part`.

## Consequences

- `*.part` files appear in the mirror folders for a while; after a successful download they are gone.
- A repeated download of a file of another size downloads it as a whole instead of appending.
- Binding `.part` to the version of the file on the site and the decision to download again by the listing time are
  [ADR-0006](0006-changed-file-detection.md).

## Addendum: locking `.part` (#15)

`synchronized` on a `FileSaveProcessor` instance excluded nothing: every file gets its own instance.
The download runs under a lock of the `.part` file itself (`FileChannel.tryLock()`, without waiting):

- inside the JVM the locks are tracked by the identity of the file (device and inode), so tasks writing one file
  under different names (names that differ in case on a case-insensitive file system) are excluded as well;
- the lock is visible to other processes, so two simultaneous runs with one destination folder do not write the same
  file;
- a task that did not get the lock skips the file with a warning; a task that got it checks the file again, so as not
  to download a file that another task has just replaced.
