# Code quality gates

## Before every PR

```bash
./gradlew ktlintCheck
./gradlew test --parallel   # or targeted module tests
```

Touched modules only (faster):

```bash
./gradlew :app:shared:ktlintCheck :server:order:ktlintCheck :server:order:test
```

## Import hygiene

- After **moving** methods to another file: clean imports in **source and destination**.
- Prefer short names; avoid `java.util.*` / long FQCNs in method bodies when imported.
- No star imports (`import foo.*`) unless already established in that file.

## Refactors

- One concern per commit.
- Prefer extract + delegate over rewriting call sites when API stability matters.
- Do not mix formatting-only changes with behavior changes.

## AI / agent contributions

See root [`AGENTS.md`](../AGENTS.md). Human review still required; CI is the backstop.
