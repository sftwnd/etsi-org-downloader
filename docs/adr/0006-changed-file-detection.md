# ADR-0006: Download decision — size and listing time, `.part` bound to the file version

- Status: Accepted
- Date: 2026-10-30
- Russian version: [ADR-0006](ru/0006-changed-file-detection.md)

## Context

`FileSaveProcessor` decided whether to download a file by its size only (#10):

- a file updated on the site with the same size stayed old but got the new time — a stale file looked up to date;
- a `.part` ([ADR-0005](0005-part-file-download.md)) could remain from a previous version of the file, and a resume
  would glue the beginning of the old version to the end of the new one;
- `If-Range` was not sent, `Content-Range` of a `206` response was not checked;
- the read loop trusted the size from the listing: if it differed from the real size, the request went beyond the end
  of the file or the last block was lost.

The listing time of www.etsi.org matches the `Last-Modified` of the file in UTC to the minute (#23). Files saved by
previous versions of the program got the listing time read in the system zone (e.g. MSK), and they differ from UTC
by a whole number of hours.

## Decision

- A local file is up to date if its size equals the size from the listing and its time equals the listing time.
- If the time differs by up to 26 hours in a multiple of 15 minutes, this is a shift of the time zone: the file time
  is set from the listing and the file is not downloaded. Any other difference of the time or the size means that the
  file has changed on the site, and it is downloaded again.
- The name of `.part` contains the listing time: `<name>.<yyyyMMddHHmm>.part`. Before a download the `.part` files of
  other versions of the same file are removed; only the `.part` of the current version is resumed.
- A `206` response must contain `Content-Range` that starts at the requested offset, otherwise it is a
  `ProtocolException` without retries. The total length from `Content-Range` or `Content-Length` must match the size
  from the listing, otherwise `.part` is removed and the file fails until the next run.
- Reading is bounded by the remaining length of the file, so extra bytes of the response are not read and the last
  block is always written.

## Rationale

- The listing time is the `Last-Modified` of the file, i.e. a version mark that the site itself provides without
  additional requests and without storing metadata.
- The difference between time zones on Earth does not exceed 26 hours and is a multiple of 15 minutes. A real change
  of a file coincides with such a shift only if it happened within a day and exactly on a quarter of an hour; otherwise
  this rule saves a full re-download of the mirror after a change of the zone (#23).
- Binding `.part` to the listing time together with the check of the total length in the response gives the same
  result as `If-Range`, but does not require storing `ETag`/`Last-Modified` between runs: only a part of the version
  described by the current listing is resumed.
- A mismatch of the length in the response and in the listing means that the listing and the file describe different
  versions; downloading by either length is not possible in this situation.

## Consequences

- A file changed on the site with the same size is downloaded again.
- After a change of the time zone the time of the files is set again once, without a re-download.
- A file that the user has changed manually (and thereby changed its time) will be downloaded again.
