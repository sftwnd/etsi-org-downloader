# ADR-0012: Build and test every change on GitHub Actions

- Status: Accepted
- Date: 2026-10-30
- Amended: 2026-10-30 — the base images are pinned by digest and updated by Dependabot (#68)
- Russian version: [ADR-0012](ru/0012-continuous-integration.md)

## Context

The repository had no continuous integration (#67): nothing built and tested the pushes and the pull requests. The tests
ran only where somebody started them, and the build stage of the Docker image ran `./mvnw verify` with all of them,
about a minute on every build of the image. They ran on JDK 11 only, although `run.sh` starts the jar with the JDK
of `JAVA_HOME` or of `PATH`, and `maven.compiler.source` and `target` 11 do not keep a newer javac from compiling
against the API of its own JDK.

## Decision

- The workflow `.github/workflows/build.yml` runs on the pushes to `master` and `develop`, on the pull requests and
  by hand, as in `crayfish-common-crc`:
  - `build` — `./mvnw -B -ntp verify`, the unit tests and `ModulePathIT` ([ADR-0011](0011-jpms-module.md)),
    on Temurin JDK 11, 17, 21 and 25: the language level and the runtime of the image, and the newer LTS versions
    `run.sh` may start the jar with;
  - `docker` — `docker compose build` and a start of the service with an invalid configuration: the exit status 2
    ([ADR-0008](0008-error-isolation-exit-code.md)) shows that the JVM, the jar and the logging configuration
    of the image start.
- The workflow only reads the repository and does not keep the token of the checkout; a new push to a pull request
  cancels the run for the previous one.
- `pom.xml` sets `maven.compiler.release` 11 instead of `maven.compiler.source` and `target`.
- The build stage of the image packages the jar without the tests (`-Dmaven.test.skip=true`).
- A pull request is merged after its checks have passed.
- The base images of the `Dockerfile` are pinned by the digests of their multi-platform indexes. Dependabot
  (`.github/dependabot.yml`) proposes the updates of the digests, of the Maven dependencies and plugins and of
  the GitHub Actions weekly, as pull requests into `develop` that the workflow builds and tests. It does not propose
  what the project cannot take: another major version of `eclipse-temurin`, JUnit 6 (Java 17) and the plugins 4
  (Maven 4).

## Rationale

- Every change is built and tested before it is merged, and the pull request shows the result.
- The matrix catches what works on one JDK only: the tests, the module path, the start of both jars. The LTS versions
  are the JDKs a user is likely to start `run.sh` with.
- `--release 11` makes the builds of the matrix equal: javac of any JDK checks the code against the Java 11 API,
  while `source` and `target` check only the language level and the class file version.
- The image is built from the sources the workflow has tested, so the tests are not repeated in every build
  of the image.
- The start with an invalid configuration needs no network: the check does not depend on www.etsi.org and puts no load
  on it.
- A digest makes the build reproducible: the same `Dockerfile` builds from the same base images until a pull request
  changes them, and every change of a base image is a commit that the workflow has checked.

## Consequences

- `docker compose up --build` does not run the tests: locally they are run by `./mvnw verify`.
- Every push runs four builds of the project and one of the image.
- Dependabot uses its configuration from the default branch, so the updates start when the file reaches `master` with
  a release; until then the digests are updated by hand.
- The newer JDKs warn about what they are going to forbid: on JDK 21 and 25 Mockito loads its agent dynamically,
  on JDK 25 Lombok calls `sun.misc.Unsafe`. The warnings do not fail the build; the matrix shows when they become
  errors.
