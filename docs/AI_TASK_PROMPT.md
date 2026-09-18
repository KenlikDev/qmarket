# Copy-paste constraints for AI tasks

Put this at the **start** of every AI coding request:

```text
Follow AGENTS.md in the repo root.

Constraints for this task:
1) Touch only files required for the stated goal
2) After any move/extract: remove unused imports in ALL touched files
3) Run ktlint on touched modules (./scripts/agent-quality-gate.sh …) and report the result
4) No drive-by refactors, no FQCN when an import exists
5) Deliver: file list + commit message + patch/zip with paths relative to repo root
6) If you cannot run ktlint, say "UNVERIFIED" — do not claim the diff is clean
```

Do not accept a zip that has no ktlint result (or explicit UNVERIFIED).
