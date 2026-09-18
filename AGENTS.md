# Agent / AI contributor rules — QMarket

These rules apply to **every** change proposed by an AI assistant or automated agent.
Human contributors should follow the same gates before opening a PR.

If a rule conflicts with a user request, **prefer the stricter quality gate** unless the user explicitly overrides it for a one-off.

---

## Non-negotiable quality gates

Before delivering **any** patch, archive, or commit suggestion:

1. **Minimal scope** — change only files required for the stated task. No drive-by refactors, no “while we’re here” cleanups outside the task.
2. **One concern per commit** — e.g. `fix: remove unused imports in QMarketAppModel`, not “refactor + imports + rename”.
3. **ktlint must pass** on every touched Gradle module:
   ```bash
   ./gradlew :app:shared:ktlintCheck :server:order:ktlintCheck :server:common:ktlintCheck
   # add every other module you modified
   ```
   Or project-wide: `./gradlew ktlintCheck`
4. **Zero unused imports** — after moving code between files, re-scan **both** the old and the new file.
5. **No FQCN noise** — do not write `java.util.Optional` / `com.kenlikdev...Product(` when the type is already imported; use the short name.
6. **No leftover dead code** — after extract/move: delete old methods, fix call sites, drop obsolete imports.
7. **Do not claim “clean” without evidence** — paste command output summary (or state clearly that ktlint could not be run in the environment).

If ktlint or compile cannot be run in the agent environment: **say so**, still do a manual unused-import audit, and mark the deliverable as **unverified**.

---

## Kotlin / project conventions

- Package layout and module boundaries: respect existing `:server:*` and `:app:shared` structure; do not introduce cross-module leaks (see ArchUnit tests).
- Prefer existing patterns: `Money.toMinorUnits`, `Authentication.userId()`, `OrderMapper`, payment via `OrderPaymentService`.
- Null-safety: prefer `requireNotNull(x) { "..." }` over `x!!` after persist.
- Coroutines (client): rethrow `CancellationException`; do not swallow it in broad `catch (Exception)`.
- Do not pre-fill production secrets or real passwords in UI state; demo defaults must be obvious and local-only.
- Compose entrypoints may be named `App()` (factory-style); do not “fix” that for ktlint naming unless the project config requires it.

---

## Documentation language

- **All project docs and user-facing README content: English only.**
- Do not mix Russian and English in the same doc file.

---

## Forbidden patterns (regressions we already hit)

| Bad | Required instead |
|-----|------------------|
| Leave unused imports after splitting a class | Remove them; verify with ktlint / IDE inspection |
| Fully-qualified type in body while import exists | Short name + import |
| Giant “pass 1–5” refactors in one go | Small PR-sized diffs, gate after each |
| Zip without file list | Always list paths + one-line commit message |
| “Professional rewrite” of the whole codebase | Only what was asked |

---

## Deliverable format (AI)

Every AI response that changes code must include:

1. **Goal** (one sentence)
2. **Files touched** (paths)
3. **Commands run** (ktlint / tests) and result
4. **Commit message** (Conventional Commits style)
5. **Patch or zip** with paths relative to repo root

Example commit messages:

```text
fix: drop unused imports after QMarketAppModel split
refactor(order): extract OrderPaymentService
docs: English-only README and ROADMAP
```

---

## Suggested local script

```bash
chmod +x scripts/agent-quality-gate.sh
./scripts/agent-quality-gate.sh app:shared server:order server:common
```

CI must keep failing on ktlint / tests; do not weaken gates to land AI diffs.
