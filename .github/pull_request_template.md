## Summary

<!-- What changed and why? -->

## Scope

- [ ] Focused change; no unrelated refactor
- [ ] Correct target branch (ai/integration, develop, or main as applicable)

## Validation

- [ ] ./scripts/agent-quality-gate.sh
- [ ] ./gradlew test --parallel --no-daemon
- [ ] CI is green or the reason for a documented exception is stated

## Risk

<!-- Note API, security, concurrency, migration, payment, or compatibility risk. -->

## Database

<!-- State "No migration" or list the migration and validation. -->

## Rollback

<!-- How can the change be safely reverted? -->

## Human handoff

AI work stops at ai/integration. Promotion from ai/integration to develop, and from develop to main, is human-controlled.
