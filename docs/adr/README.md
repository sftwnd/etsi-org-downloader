# Architecture decision records

The decisions are kept in two languages: in English in this folder and in Russian in [ru](ru/README.md).
A new decision gets both versions under the same file name, the versions link to each other,
and the decision is added to both indexes.

| ADR | Decision | Russian version |
|---|---|---|
| [0001](0001-destination-local-paths.md) | Map site resources to local paths through Destination | [0001](ru/0001-destination-local-paths.md) |
| [0002](0002-link-policy.md) | Crawl link policy — LinkPolicy | [0002](ru/0002-link-policy.md) |
| [0003](0003-http-timeouts-retries.md) | HTTP access — HttpURLConnection with timeouts and retries | [0003](ru/0003-http-timeouts-retries.md) |
| [0004](0004-bounded-executor.md) | Concurrency model — all resource processing in one bounded pool | [0004](ru/0004-bounded-executor.md) |
| [0005](0005-part-file-download.md) | File download model — `.part` and atomic replacement | [0005](ru/0005-part-file-download.md) |
| [0006](0006-changed-file-detection.md) | Download decision — size and listing time, `.part` bound to the file version | [0006](ru/0006-changed-file-detection.md) |
| [0007](0007-summary-result.md) | Processing result — a Summary value instead of a stream of paths | [0007](ru/0007-summary-result.md) |
| [0008](0008-error-isolation-exit-code.md) | Error isolation and the exit status | [0008](ru/0008-error-isolation-exit-code.md) |
| [0009](0009-expire-after-actual-version.md) | Remove expired versions after the actual version has been processed successfully | [0009](ru/0009-expire-after-actual-version.md) |
| [0010](0010-maven-wrapper.md) | Build with the Maven Wrapper and the project settings | [0010](ru/0010-maven-wrapper.md) |
| [0011](0011-jpms-module.md) | The downloader is a named module (JPMS) | [0011](ru/0011-jpms-module.md) |
| [0012](0012-continuous-integration.md) | Build and test every change on GitHub Actions | [0012](ru/0012-continuous-integration.md) |
| [0013](0013-settings-sources.md) | Settings — a system property, otherwise an environment variable, otherwise the default | [0013](ru/0013-settings-sources.md) |
| [0014](0014-run-lock.md) | One run at a time per destination folder | [0014](ru/0014-run-lock.md) |
| [0015](0015-dry-run.md) | Dry run — the crawl of a run with a plan instead of downloads and removals | [0015](ru/0015-dry-run.md) |
| [0016](0016-resource-limits.md) | Bounded resources — limits of the container, the heap as their share, an exit on OutOfMemoryError | [0016](ru/0016-resource-limits.md) |
| [0017](0017-jlink-runtime-image.md) | The image runs the module in a runtime built by jlink on a distroless system | [0017](ru/0017-jlink-runtime-image.md) |
