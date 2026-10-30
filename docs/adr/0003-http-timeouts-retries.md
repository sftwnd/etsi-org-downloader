# ADR-0003: HTTP access — HttpURLConnection with timeouts and retries

- Status: Accepted
- Date: 2026-10-30
- Russian version: [ADR-0003](ru/0003-http-timeouts-retries.md)

## Context

`Page` opened `HttpURLConnection` with the default settings (#11):

- `connectTimeout` and `readTimeout` were `0` — an unlimited wait: one stalled connection blocked the run forever;
- 5xx, 429 and network failures were not retried;
- on a response other than 200/206 the connection was not released, and the `connection` field stayed set while
  `inputStream == null`, which led to an NPE on the next call;
- reading a listing did not handle the end of stream, and the text was decoded with the platform charset.

Crawling `/deliver/` means hundreds of thousands of requests in a run of many hours, so network and server failures
are a normal situation. www.etsi.org is now served through Cloudflare (`server: cloudflare`): listings come with
`Content-Type: text/html; charset=UTF-8`, files with `Accept-Ranges: bytes`, `Last-Modified` and `ETag`.

## Decision

Stay with `HttpURLConnection` and add to `Page`:

- `HttpSettings` — a connect timeout of 30 s, a read timeout of 60 s, 3 attempts, a delay of 1 s before the second
  attempt and twice as long before every next one;
- a retry after a network error and on `408`, `429`, `5xx` responses; if the server sent `Retry-After` in seconds and
  it is longer than the computed delay, it is used, but not longer than a minute; other responses are a final error;
- the page fields change only when a connection is opened successfully; on a failure the response stream and the
  connection are released; `disconnect()` releases the connection explicitly;
- `charset()` — the charset from `Content-Type`, UTF-8 by default;
- a listing is read with `readNBytes`, an early end of stream is an `EOFException`;
- a read error during a file download continues the download from the current offset with a new connection
  (the same mechanism as after a premature end of stream).

## Rationale

- `HttpURLConnection.setReadTimeout` limits every read, so a stall in the middle of a large file is detected.
  In Java 11 the request timeout of `java.net.http.HttpClient` applies only until the response headers are received;
  reading the body is not limited by it. Switching would mean rebuilding `Page` and its tests without any gain
  in reliability.
- Only failures that may pass by themselves are retried: network errors, overload (`429`, `503`) and server errors.
  `404` and other client errors will not change on a retry.
- An exponential delay and `Retry-After` reduce the load on the site during failures. The upper limit of one minute
  keeps a single resource from holding a thread for long: a resource that could not be fetched will be taken in the
  next run.
- The timeouts are chosen with a margin for large files and slow links; they are collected in `HttpSettings`,
  so the tests use short values, and a change of the defaults touches one place.

## Consequences

- A run no longer hangs on one connection: at worst a resource fails after three attempts.
- Waiting before a retry occupies a pool thread; with three attempts it is a few seconds at most, with `Retry-After` —
  up to a minute.
- A read error of a listing body is not retried within a run: listings are small, such a listing will be processed
  in the next run.
