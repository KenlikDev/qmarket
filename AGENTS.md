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

## 4. Scope discipline

- One logical concern per PR.
- Do not mix formatting-only changes with behavior changes.
- Do not perform unrelated cleanup while implementing a feature/fix.
- Preserve historical database migrations; add forward migrations instead of rewriting applied migrations.
- Preserve module boundaries. Run the architecture tests after dependency/module changes.
- Prefer existing project abstractions over introducing parallel patterns.

## 5. Mandatory quality gates

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

Never disable or weaken a CI gate to make a branch mergeable.

## 6. Kotlin / Spring / KMP conventions

- Keep :server:common free of feature-specific contracts; use dedicated *.api modules for contracts.
- Prefer requireNotNull(value) { "..." } over !!.
- Never swallow CancellationException in broad coroutine exception handlers.
- Keep payment-provider calls outside database transactions when an external network call does not require an open transaction.
- Preserve idempotency, optimistic locking, database constraints, and deterministic pagination semantics.
- Treat Flyway as the schema source of truth.
- Keep production configuration secure-by-default.
- Never add real credentials, access tokens, API keys, private URLs, or secrets to source or tests.
- Demo credentials, when required, must be clearly local/dev-only and must not appear in production configuration, logs, or API descriptions.

## 7. Imports and dead code

After extracting or moving code:

- clean imports in both source and destination files;
- remove obsolete methods and call sites;
- avoid fully-qualified type names when an import is appropriate;
- do not leave generated bin/, build output, IDE caches, or other derived artifacts tracked by Git;
- run ktlint on every touched Kotlin module.

## 8. Database and API safety

Any change involving persistence or API contracts must explicitly check:

- migration ordering and deployment assumptions;
- foreign keys, uniqueness, indexes, and delete/update semantics;
- transaction boundaries and concurrency behavior;
- backward compatibility of public API responses;
- validation at the correct service/controller boundary;
- stable error responses;
- tests for race conditions when correctness depends on ordering.

Never edit an already-published Flyway migration to "fix" the schema. Add a new migration.

## 9. PR requirements

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

## 10. Commit messages

Use Conventional Commits:

~~~
feat(catalog): add category filtering
fix(order): prevent duplicate checkout
refactor(identity): isolate OAuth verification
chore(ci): run CI on integration branches
docs(workflow): document AI integration policy
~~~

Avoid commits such as "update", "fix stuff", or giant multi-purpose "pass" commits.

## 11. Merge messages for human-controlled promotion

When a human is expected to merge ai/integration into develop, or develop into main, the AI should provide a proposed merge title and body.

The AI must not perform those merges.

A promotion message should include:

- release/integration scope;
- notable behavior and schema changes;
- CI status;
- security-impact summary;
- known limitations and follow-up work.

## 12. Branch cleanup

After a temporary branch PR is merged or closed:

- delete the temporary remote branch;
- do not reuse a completed branch for a new task;
- rely on GitHub automatic head-branch deletion for merged PRs where possible.

The permanent branches main, develop, and ai/integration must never be deleted.

## 13. Documentation

- Repository documentation and user-facing README text are English-only.
- Keep this file current when development policy changes.
- If GitHub UI configuration is required, document the exact expected setting in docs/GIT_WORKFLOW.md.

## 14. Final AI response

Any response for a code change must report:

1. Goal
2. Files changed
3. Validation commands and results
4. Commit/PR message
5. Remaining verification or human-review items

Do not claim a build, test, review, or merge happened unless there is evidence.