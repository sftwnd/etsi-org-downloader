# ADR-0002: Crawl link policy — LinkPolicy

- Status: Accepted
- Date: 2026-10-30
- Russian version: [ADR-0002](ru/0002-link-policy.md)

## Context

`TextHtmlProcessor` followed any link from a listing (#5):

- `//evil.example/deliver/...` — the request went to a foreign host, while the response was stored under ETSI paths;
- `http://...` — a download without TLS;
- `/run.sh` — a path outside `/deliver/`; with `-Ddest=.` it is a file of the project, and `Destination`
  ([ADR-0001](0001-destination-local-paths.md)) lets such a path through: it is inside the destination folder;
- `ftp:`, `file:`, `jar:`, `mailto:` — `ClassCastException` or NPE aborted the whole run;
- there was no set of visited links and no depth limit: a link to an ancestor or a cyclic folder on the server led
  to an endless crawl.

The start URI from `-Duri` was not validated either.

## Decision

Introduce `LinkPolicy`, which is created from the start URI and decides which links to follow:

- the start URI is `http`/`https` only, with a host, without user info, query and fragment, with a safe path;
  it is normalized: the scheme and the host in lower case, the path ends with `/`;
- a link is accepted if it has the same scheme, host and port (the default port included), no user info, query and
  fragment, and a safe path (the same segment rules as in `Destination`: the shared `ResourcePath`) inside the start
  folder, no deeper than `MAX_DEPTH = 32` segments;
- `visit(uri)` registers the resource in a thread-safe set of visited resources and returns `false` on a repeated
  visit; the key is the segments of the decoded path, so `/a/b` and `/a/b/` are one resource.

`TextHtmlProcessor` drops rejected links (a WARN with the escaped URI) **before** the actual version is chosen, and
drops resources met again before they are processed. `Main` builds the policy from `-Duri`; an invalid URI is an error
that ends the run without crawling. `Page` additionally refuses a non-HTTP connection.

## Rationale

- Links are filtered before the versions are chosen: otherwise a foreign folder `99.00.00_60` would become "actual",
  and all the real versions would be removed.
- The segment rules are shared with `Destination` (DRY): a path accepted by the policy is guaranteed to map into the
  destination folder.
- `MAX_DEPTH` covers the ETSI structure (`deliver/etsi_ts/<range>/<document>/<version>/<file>`) with a margin and
  limits the growth of the path with cyclic folders on the server; repetitions within the depth are cut off by the set
  of visited resources.
- Comparing the scheme and the port forbids switching from HTTPS to HTTP. Redirects are handled by
  `HttpURLConnection`: it does not follow them across protocols, and the response is stored under the requested path,
  which has already been checked by the policy.

## Consequences

- Only the start folder is mirrored; links outside it are skipped with a WARN.
- The set of visited resources grows with the number of resources of the tree — a list of segments per resource.
- A start with `-Duri` that is not `http`/`https` or has a query ends with a configuration error.
