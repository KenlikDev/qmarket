# QMarket AI repository review standard

This document defines the minimum method for a professional repository review. It is a review protocol, not a claim that the current repository satisfies it.

## 1. Establish the review target

- Resolve the exact branch and commit requested by the human.
- Record the commit SHA before reviewing.
- Do not substitute the repository default branch for the requested target.
- Treat main, develop, and ai/integration as separate states and compare them explicitly when release or integration behavior is relevant.
- Review the current tree, build configuration, generated or derived artifacts, migrations, workflows, and deployment files.

## 2. Evidence rules

Do not trust README files, roadmaps, comments, existing tests, existing issues, or previous reviews as proof of correctness. They are inputs to verify.

Every material finding must be supported by direct source/configuration evidence, reproducible execution or a failing test, a database/schema fact, or an authoritative external specification when platform/provider behavior is involved.

Separate observed facts from inferred risks and deliberate design choices. Do not promote an inference to a bug without a concrete failure mode.

## 3. Required technical passes

### Build and supply chain
Inspect Gradle settings, dependency resolution, plugin versions, Java/Kotlin/Android targets, reproducibility, deprecated libraries, dependency update policy, and whether every claimed build target has a real CI job.

### Architecture
Reconstruct module dependencies from imports and build files. Verify API/internal boundaries, Spring/JPA leakage, transaction ownership, repository access, cyclic dependencies, and package-level architecture tests. Do not assume ArchUnit rules cover the whole architecture; inspect what they actually assert.

### Database
Read every migration in order. Verify primary/foreign keys, uniqueness, nullability, indexes, checks, delete semantics, optimistic-lock fields, numeric precision, schema/API compatibility, rollout ordering, and cleanup/retention for growing tables.

### Security and identity
Threat-model login, registration, refresh, logout, password change, account disablement, roles, OAuth, token storage, CORS/CSRF, forwarded headers, secrets, error handling, and logging. Check timing, enumeration, replay, race, privilege, and token-lifetime behavior.

### Concurrency and transactions
For every mutable aggregate, identify lock order, isolation assumptions, optimistic/pessimistic locking, transaction boundaries, retries, and external side effects. Enumerate adversarial interleavings: two writers, retry after timeout, cancellation during external work, duplicate webhook, out-of-order event, stale client, and concurrent login/refresh/logout.

### Payments and external providers
Verify amount/currency identity, idempotency, authorization state, provider references, webhook authenticity, replay behavior, duplicate/out-of-order events, cancellation/refund behavior, and the exact boundary between local transactions and external network calls. Test the provider-success/local-failure and provider-failure/local-success directions.

### API contract
For every endpoint verify authentication/authorization, input bounds, normalization, output fields, error codes, status codes, pagination, sorting, partial-update semantics, object ownership, and backward compatibility. Compare server DTOs with shared/client DTOs.

### Client/platform matrix
Review common state management plus Android, iOS, JVM/Desktop, JS, and Wasm independently. Verify URL configuration, TLS/ATS/network security, token persistence, process death/restart, cancellation, retry, refresh races, accessibility, pagination, and release-vs-debug behavior. A Linux/JVM test does not prove a native iOS/Android build.
When a runnable target is available in the review environment, dynamically launch the affected application and execute the relevant user flow. For UI behavior, actually click/tap the affected controls and verify navigation, state changes, visible errors, network outcomes, and retry/cancellation behavior. Use both a successful scenario and a representative failure/retry scenario when applicable.

Runtime verification is mandatory for runnable UI targets; static review and CI are complementary, not substitutes. When runtime execution is unavailable, record the exact limitation and mark the runtime portion UNVERIFIED rather than inferring behavior from code.


### CI/CD and operations
Verify required checks actually run on every protected branch and PR, workflow permissions are least-privilege, third-party actions/dependencies are maintained and reasonably pinned, artifacts are safe, deployment config is valid, Docker runtime is non-root, health/metrics exposure is deliberate, and production configuration fails closed.

### Tests
Inspect tests for correctness rather than counting them. Look for false-positive tests, mocks that bypass important behavior, missing persistence assertions, missing transaction semantics, missing concurrency interleavings, and platform gaps. Prefer tests that would fail for the suspected defect.

### Documentation and repository hygiene
Check that documentation describes the current code and process, version numbers agree, paths exist, examples are executable, and ignored/generated files are absent from the current Git tree. Documentation drift is a finding when it can materially mislead maintainers or users.

## 4. Severity

- P0 — exploitable security/data-loss/financial-integrity failure or release-blocking correctness defect with a credible path in scope.
- P1 — serious security, financial, data-integrity, concurrency, or production-functional defect requiring prompt remediation.
- P2 — meaningful reliability, maintainability, UX, deployment, or defense-in-depth issue without immediate catastrophic impact.
- P3 — backlog improvement, optimization, polish, or scale concern.

Severity must be justified by preconditions and actual impact, not by how unusual the code looks.

## 5. Finding format

Each confirmed finding should state: title/severity; exact ref/commit; affected file and symbol/area; preconditions and failure sequence; impact; evidence or reproduction; remediation; acceptance criteria; and tests that prove the fix.

## 6. Issue discipline

Create a GitHub issue for an independent root cause when tracking helps implementation. Do not create duplicate issues for symptoms of one underlying defect. Do not create speculative issues merely because a pattern could be improved.

Use focused PRs for fixes. Review/workflow changes belong in documentation/workflow PRs; application fixes belong in separate implementation PRs unless the human explicitly asks otherwise.

## 7. Review completion rule

The review is not complete until the reviewer states which areas were inspected, which were dynamically verified, which were statically checked, which were compared against external specifications, and which could not be verified in the available environment.