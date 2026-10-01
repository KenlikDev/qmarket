# Copy-paste constraints for AI tasks

Put this at the **start** of every AI coding request:

```text
Follow AGENTS.md in the repo root.

Constraints for this task:
1) Before each major phase, reread AGENTS.md and the relevant task/review/workflow documentation
2) After context compaction or resume, reread AGENTS.md before doing any further work
3) Touch only files required for the stated goal
4) After any move/extract: remove unused imports in ALL touched files
5) Run ktlint on touched modules (./scripts/agent-quality-gate.sh …) and report the result
6) No drive-by refactors, no FQCN when an import exists
7) Before opening/retargeting/merging a PR, verify the current target SHA, required checks, and branch freshness
8) Deliver: file list + commit message + patch/zip with paths relative to repo root
9) If you cannot run ktlint, say "UNVERIFIED" — do not claim the diff is clean
```

Do not accept a zip that has no ktlint result (or explicit UNVERIFIED).


## Repository review

For repository-wide audits, follow `docs/AI_REVIEW_STANDARD.md`. Verify the exact target ref, treat source/tests/docs as untrusted evidence, create focused issues only for confirmed root causes, and report any unverified native/platform checks explicitly.

## Instruction refresh

AI work must follow the event-based instruction refresh points in AGENTS.md: task-phase boundaries, context resume/compaction, branch rebases or retargets, and before PR/merge decisions.
