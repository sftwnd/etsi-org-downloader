# ADR-0001: Map site resources to local paths through Destination

- Status: Accepted
- Date: 2026-10-30
- Amended: 2026-10-30 — the destination folder is checked before the crawl (#59)
- Russian version: [ADR-0001](ru/0001-destination-local-paths.md)

## Context

The local path of a resource was built as `Path.of(dest, uri.getPath())` in `FileSaveProcessor` and in `Main` when
expired versions were removed. `uri.getPath()` returns the decoded but not normalized path, and `URI.resolve` does not
normalize the absolute path of a link. A link from a listing could therefore address any file the process may write (#4):

- `/../../tmp/x` → `./../../tmp/x`;
- `/deliver/%2E%2E/%2E%2E/x` → `./deliver/../../x`: `%2E%2E` turns into `..` only when it is decoded,
  so neither a check of the raw string nor `URI.normalize()` sees it.

A listing is untrusted data: the site or its CDN may be compromised, `-Duri` may point to another server.

## Decision

Introduce `Destination`, the only place where a resource URI becomes a local path:

- the root is the absolute normalized path of the destination folder;
- the local path is built from the segments of the **decoded** resource path: `root.resolve(segment)` for every segment;
- rejected are: a path that does not start at the site root, an encoded separator (`%2F`, `%5C`), an empty segment,
  `.` and `..`, `\`, control and format characters, and a segment that adds anything but exactly one name to the path
  on the platform (e.g. `C:` on Windows);
- the final safety check is `path.normalize().startsWith(root)`;
- a violation is an `IllegalArgumentException` with the text escaped by `Printable`; the resource is skipped with an
  error in the log.

`FileSaveProcessor` takes the file path only from `Destination`; `Main` maps expired versions the same way before
removing them, so `onExpires` receives URIs instead of decoded paths.

Before the crawl `Destination.prepare()` creates the destination folder and creates and deletes a probe file
`.etsiorg-*.probe` in it; if it fails, the run ends at once as an invalid configuration with the status 2
([ADR-0008](0008-error-isolation-exit-code.md)).

## Rationale

- What is checked is what is actually used to access the file system: the path after decoding and resolving,
  not the raw string of the link.
- The segment check does not allow `..` in the path at all, and `startsWith` guards against platform specifics —
  two independent checks.
- One component instead of scattered `Path.of(root, path)` calls (SRP, DRY): the mapping rules change in one place and
  are covered by one set of tests.
- `toRealPath` is not used: the file may not exist yet. Symbolic links on removal are a separate task (#6).
- The check costs one file before the first request: without it a run with a read-only destination crawled all
  the listings, failed on every file and ended with the status 1 (#59). Creating a file checks what a run does;
  the permission bits of the folder say nothing about a read-only file system or a volume of another user.

## Consequences

- Valid paths are mapped as before: the local structure repeats the path on the site (`<dest>/deliver/...`).
- Paths in the log are absolute.
- A resource with an invalid path is neither downloaded nor removed. Restricting the crawl to the start folder and host
  is a separate decision (#5).
- A destination folder that cannot be written is reported before any request to the site, with the cause,
  e.g. `Read-only file system` or `AccessDeniedException`.
