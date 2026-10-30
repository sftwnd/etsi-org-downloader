# ADR-0004: Concurrency model — all resource processing in one bounded pool

- Status: Accepted
- Date: 2026-10-30
- Amended: 2026-10-30 — 4 threads by default instead of the number of processors (#57); `Settings` and `RunLock`
  in the component diagram (#61, #62)
- Russian version: [ADR-0004](ru/0004-bounded-executor.md)

## Context

`TextHtmlProcessor` started processing child resources with `CompletableFuture.supplyAsync` without an executor (#12),
and the pool from `Main` only merged the results:

- with 2 or fewer available CPUs (a usual container limit) `CompletableFuture` creates a new thread for every task
  (`ThreadPerTaskExecutor`) — a thread and a connection per listing line, thousands of threads, a risk of
  `OutOfMemoryError: unable to create native thread` and of an IP ban by the site;
- with more CPUs blocking I/O ran in `ForkJoinPool.commonPool()` with parallelism CPU−1 that cannot be configured;
- if the asynchronous stage had already completed when `thenApply` was attached, the child resource, a download
  included, was processed synchronously in the parent's thread, `main` included;
- the requests presented themselves as a browser: `User-Agent: Mozilla/5.0`.

## Decision

- The whole processing of a resource — creating the `Page`, choosing the processor, parsing a listing or
  downloading — runs as a task `supplyAsync(..., executor)` of the pool from `Main`; the result is unwrapped with
  `thenCompose(identity())`, and merging the results of the children (`allOf`) creates no separate tasks.
- The pool size is set with the `-Dthreads` property, 4 by default; the executor is mandatory
  for `ComplexProcessorFactory` and `TextHtmlProcessor`.
- Tasks do not wait for each other with blocking calls: a parent returns a future and does not hold a thread while
  its children are processed.
- `User-Agent: etsiorg-downloader (+https://github.com/sftwnd/etsi-org-downloader)`.

## Rationale

- The number of threads and simultaneous connections is limited and predictable on any machine and in a container,
  and it does not depend on the implicit rules `CompletableFuture` uses to choose a pool.
- A separate semaphore for the number of requests is not needed (KISS): every HTTP request runs as a task of the pool,
  so a pool of N threads gives at most N simultaneous requests.
- The default is chosen for the load on the site, not for the machine: the number of processors has nothing to do with
  it, and a container on a machine with 16 processors sees all 16 of them, which made 16 simultaneous requests to
  www.etsi.org. 4 requests are fewer than the 6 connections to one host a browser opens over HTTP/1.1.
- Without blocking waits inside a fixed-size pool, a deadlock where all the threads wait for tasks that have no thread
  to run in is impossible; composition with `thenCompose` keeps this property.
- An honest User-Agent lets the site administrators see who is accessing the site and get in touch; it has been
  checked that www.etsi.org answers such a request with `200`.

## Consequences

- The pool queue is not bounded: it holds the tasks for the resources found but not processed yet, so memory grows
  with the "front" of the crawl, not with the number of threads.
- By default the parallelism is 4 on any machine; `-Dthreads` raises it for a fast link or lowers it for a gentle
  mode, e.g. `-Dthreads=2`.

## Component diagram (C4, level 3)

```mermaid
C4Component
    title Components of etsiorg-downloader
    Person(user, "User", "run.sh or docker compose")
    System_Ext(etsi, "www.etsi.org", "Listings and documents of /deliver/")
    Container_Boundary(app, "etsiorg-downloader, JVM") {
        Component(main, "Main", "Java", "Arguments, thread pool, summary, exit status")
        Component(settings, "Settings", "Java", "System properties, ETSIORG_* variables, defaults")
        Component(policy, "LinkPolicy", "Java", "Accepted links, visited resources")
        Component(factory, "ComplexProcessorFactory", "Java", "Choice of the resource processor")
        Component(html, "TextHtmlProcessor", "Java", "Listing parsing, version choice, children in the pool")
        Component(file, "FileSaveProcessor", "Java", "File download with resume")
        Component(page, "Page", "Java", "HTTP: timeouts, retries, Range")
        Component(dest, "Destination", "Java", "URI to a path inside the destination folder")
        Component(remover, "ExpiredRemover", "Java", "Removal of expired versions")
        Component(lock, "RunLock", "Java", "One run at a time per destination folder")
    }
    ContainerDb(fs, "Destination folder", "File system", "Mirror deliver/...")
    Rel(user, main, "Starts")
    Rel(main, settings, "Settings of the run")
    Rel(main, lock, "Holds while the run lasts")
    Rel(main, factory, "Root of the crawl")
    Rel(factory, html, "Folder")
    Rel(factory, file, "File")
    Rel(html, policy, "Checks links")
    Rel(html, factory, "Children, as pool tasks")
    Rel(html, remover, "Expired versions")
    Rel(html, page, "Listing")
    Rel(file, page, "Content")
    Rel(page, etsi, "HTTPS GET")
    Rel(file, dest, "File path")
    Rel(remover, dest, "Version path")
    Rel(file, fs, "Writes")
    Rel(remover, fs, "Removes")
    Rel(lock, fs, "Locks .etsiorg-downloader.lock")
```
