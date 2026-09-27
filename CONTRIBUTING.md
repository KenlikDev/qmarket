# Contributing

## Development flow

QMarket uses three permanent branches:

- main — production/release branch; human merge only.
- develop — human-owned integration branch.
- ai/integration — AI staging branch.

AI work must happen on temporary branches and be integrated into ai/integration. AI must not merge into develop or main.

## Quality gates

1. Run ./scripts/agent-quality-gate.sh.
2. Run ./gradlew test --parallel --no-daemon.
3. Keep commits focused on one concern.
4. Documentation and README content must be English.
5. Follow AGENTS.md for AI-assisted changes.

See docs/CODE_QUALITY.md and docs/GIT_WORKFLOW.md.
