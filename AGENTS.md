# AI / Agent Development Rules — QMarket

These rules define the engineering contract for AI-assisted development and are intentionally stricter than the minimum GitHub defaults.

## 1. Branch model

QMarket has exactly three permanent branches:

- **main** — production/release history. Human-only merge target.
- **develop** — human-owned integration branch. Human-only merge target.
- **ai/integration** — AI integration/staging branch. AI may integrate reviewed temporary work here.

All other branches are temporary work branches. Recommended names:

- feat/<short-name>
- fix/<short-name>
- refactor/<short-name>
- chore/<short-name>
- docs/<short-name>

Never create new permanent branches without an explicit human decision.

## 2. AI permissions and merge rules

GitHub required approvals are set to **0** for all three permanent branches because the repository currently has one human owner. The human must still perform and own promotion review for `ai/integration → develop` and `develop → main`; a self-approval is neither required nor possible.

The AI:

1. May create, update, and remove commits only on temporary branches and ai/integration.
2. MUST NOT push commits directly to main or develop.
3. MUST NOT merge any pull request into main or develop.
4. MUST NOT use force-pushes to protected branches.
5. Should normally develop on a temporary branch and open a PR into ai/integration.
6. May merge a temporary-branch PR into ai/integration only after all automated checks required by the repository are green.
7. Must stop at the ai/integration → develop boundary. A human owns this promotion.
8. Must stop at the develop → main boundary. A human owns this promotion.

Expected flow:

~~~
temporary branch
      |
      +-- PR --> ai/integration
                    |
                    +-- human review / merge --> develop
                                                   |
                                                   +-- human review / merge --> main
~~~

## 3. Start-of-task procedure

Before changing code:

1. Read the relevant issue/PR and inspect the current branch.
2. Start from the current ai/integration state unless a human explicitly specifies another base.
3. Check whether the task is already implemented in another branch/PR.
4. Identify affected modules, migrations, APIs, and tests.
5. Define the smallest safe change set.

Do not build on stale refactor branches when ai/integration contains the current integration state.

## 4. Instruction refresh cadence

AI context can be long-lived and may be compacted or resumed. To prevent instruction drift, refresh the repository rules at deterministic boundaries.

The AI MUST re-read:

- AGENTS.md at the start of every task phase;
- docs/AI_REVIEW_STANDARD.md for repository-wide reviews and whenever the review scope changes;
- docs/GIT_WORKFLOW.md before any branch, PR, or merge decision;
- relevant module/domain documentation before changing that subsystem.

The AI MUST refresh these instructions again:

1. after context compaction, conversation resume, or any interruption that may have discarded working context;
2. after rebasing, retargeting, or otherwise changing the reviewed base ref;
3. before modifying CI/CD, branch rules, authentication, payments, database migrations, or public API contracts;
4. before opening, retargeting, approving, or merging a PR;
5. before declaring a temporary branch ready for integration;
6. after resolving conflicts or incorporating another branch.

Before a merge into ai/integration, verify all of the following against the current remote state:

- current ai/integration commit SHA;
- PR base and head SHA;
- required CI checks for that target;
- unresolved review threads;
- database/API/concurrency impact;
- whether the branch contains changes already integrated elsewhere.

Instruction refresh is an engineering control, not optional reading.

## 5. Scope discipline

- One logical concern per PR.
- Do not mix formatting-only changes with behavior changes.
- Do not perform unrelated cleanup while implementing a feature/fix.
- Preserve historical database migrations; add forward migrations instead of rewriting applied migrations.
- Preserve module boundaries. Run the architecture tests after dependency/module changes.
- Prefer existing project abstractions over introducing parallel patterns.

## 6. Mandatory quality gates

Before considering a change ready for integration:

~~~
./scripts/agent-quality-gate.sh
./gradlew test --parallel --no-daemon
~~~

For a smaller iteration, run the affected module checks first, then run the full project gate before integration.

Required checks include:

- ktlint
- unit tests
- integration tests
- architecture tests when relevant
- JaCoCo/coverage checks when the affected modules are covered by the project policy

CI is authoritative. If local execution is impossible, say so explicitly and mark the change UNVERIFIED.

### 6.1 Runtime / UI verification
When the project or affected artifact is runnable in the available environment, AI MUST perform a real runtime smoke test in addition to source inspection and CI.

For application/UI work this means:
- launch the actual application target that is available (for example Android, iOS simulator, JVM/Desktop, JS, or Wasm);
- exercise the main affected user flow with real input;
- click/tap the relevant controls and verify the resulting state, navigation, network behavior, and errors;
- test at least one happy path and one failure/cancellation/retry path when the feature has them;
- record the exact target and commands/scenarios used in the PR validation section.

Static analysis, unit tests, screenshots, or CI compilation do not count as a substitute for a runnable smoke test when a runnable target is available.

If the target cannot be launched or interacted with in the available environment, explicitly mark runtime verification UNVERIFIED and state the concrete environmental/tooling limitation. Never claim that a button, screen, or end-to-end flow was manually tested when it was not.

Never disable or weaken a CI gate to make a branch mergeable.

A PR is not integration-ready merely because its code looks correct. Its PR head must be based on the current target branch and its required checks must be green for that actual head SHA.

## 7. Kotlin / Spring / KMP conventions

- Keep :server:common free of feature-specific contracts; use dedicated *.api modules for contracts.
- Prefer requireNotNull(value) { "..." } over !!.
- Never swallow CancellationException in broad coroutine exception handlers.
- Keep payment-provider calls outside database transactions when an external network call does not require an open transaction.
- Preserve idempotency, optimistic locking, database constraints, and deterministic pagination semantics.
- Treat Flyway as the schema source of truth.
- Keep production configuration secure-by-default.
- Never add real credentials, access tokens, API keys, private URLs, or secrets to source or tests.
- Demo credentials, when required, must be clearly local/dev-only and must not appear in production configuration, logs, or API descriptions.

## 8. Imports and dead code

After extracting or moving code:

- clean imports in both source and destination files;
- remove obsolete methods and call sites;
- avoid fully-qualified type names when an import is appropriate;
- do not leave generated bin/, build output, IDE caches, or other derived artifacts tracked by Git;
- run ktlint on every touched Kotlin module.

## 9. Database and API safety

Any change involving persistence or API contracts must explicitly check:

- migration ordering and deployment assumptions;
- foreign keys, uniqueness, indexes, and delete/update semantics;
- transaction boundaries and concurrency behavior;
- backward compatibility of public API responses;
- validation at the correct service/controller boundary;
- stable error responses;
- tests for race conditions when correctness depends on ordering.

Never edit an already-published Flyway migration to "fix" the schema. Add a new migration.

## 10. PR requirements

Every AI-generated PR should contain:

### Summary
What changed and why.

### Risk / behavior
What behavior, API, schema, security, or concurrency semantics changed.

### Validation
Exact commands run and the result. Distinguish VERIFIED from UNVERIFIED.

### Database
State whether migrations were changed and how they were validated.

### Rollback
Explain how to revert the change safely, especially for schema or payment changes.

### Human handoff
State that the PR targets ai/integration and is ready for human promotion to develop only after review.

## 11. Commit messages

Use Conventional Commits:

~~~
feat(catalog): add category filtering
fix(order): prevent duplicate checkout
refactor(identity): isolate OAuth verification
chore(ci): run CI on integration branches
docs(workflow): document AI integration policy
~~~

Avoid commits such as "update", "fix stuff", or giant multi-purpose "pass" commits.

## 12. Merge messages for human-controlled promotion

When a human is expected to merge ai/integration into develop, or develop into main, the AI should provide a proposed merge title and body.

The AI must not perform those merges.

A promotion message should include:

- release/integration scope;
- notable behavior and schema changes;
- CI status;
- security-impact summary;
- known limitations and follow-up work.

## 13. Branch cleanup

After a temporary branch PR is merged or closed:

- delete the temporary remote branch;
- do not reuse a completed branch for a new task;
- rely on GitHub automatic head-branch deletion for merged PRs where possible.

The permanent branches main, develop, and ai/integration must never be deleted.

## 14. Documentation

- Repository documentation and user-facing README text are English-only.
- Keep this file current when development policy changes.
- If GitHub UI configuration is required, document the exact expected setting in docs/GIT_WORKFLOW.md.

## 15. Review standard

Repository-wide review tasks must follow docs/AI_REVIEW_STANDARD.md. The reviewer must resolve the exact requested branch/commit, treat source code, tests, documentation, and prior reviews as untrusted evidence, distinguish facts from risks/design choices, test concurrency and transaction interleavings, and explicitly report platform checks that cannot be executed.

AI may create focused GitHub issues for confirmed independent root causes. Each issue must include the reviewed ref/commit, affected area, concrete failure sequence, impact, evidence or reproduction, remediation, acceptance criteria, and required tests. Do not create speculative or duplicate issues.

## 16. Final AI response

Any response for a code change must report:

1. Goal
2. Files changed
3. Validation commands and results
4. Commit/PR message
5. Remaining verification or human-review items

Do not claim a build, test, review, or merge happened unless there is evidence.