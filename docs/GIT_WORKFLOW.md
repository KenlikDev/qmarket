# GitHub development workflow

## Permanent branches

| Branch | Purpose | Who may merge into it |
|---|---|---|
| main | Production/release history | Human owner |
| develop | Human-owned integration | Human owner |
| ai/integration | AI integration/staging | AI may merge temporary PRs after CI |

All other branches are temporary.

## Development flow

~~~
feat/* / fix/* / refactor/* / chore/* / docs/*
                    |
                    | pull request
                    v
             ai/integration
                    |
                    | human review + merge
                    v
                 develop
                    |
                    | human review + merge
                    v
                  main
~~~

AI must never merge a normal AI work PR into develop or main. Dependabot PRs targeting develop are an explicit exception: AI may manage, merge, and delete those temporary dependency branches after the required CI checks pass.

## Required GitHub protection

Configure these three branches under **Settings → Branches / Rulesets**.

### main

Require:

- pull request before merging;
- **0 required GitHub approvals** in the current single-human workflow;
- human review of the promotion PR before merge as a process requirement;
- dismiss stale approvals when new commits are pushed;
- status checks: test and secret-scan;
- branch must be up to date before merge;
- all review conversations resolved;
- no force pushes;
- no deletion;
- enforce rules for administrators;
- linear history / squash-or-rebase only.

### develop

Use the same protection as main:

- **0 required GitHub approvals** because the sole repository owner cannot approve their own PR;
- human review of the promotion PR is still required as the release process;
- CI checks remain mandatory.

### ai/integration

Require:

- pull request before merging;
- status checks: test and secret-scan;
- branch must be up to date before merge;
- all review conversations resolved;
- no force pushes;
- no deletion.

Do not require a human approval on ai/integration. CI is the automated gate here; human review happens before promotion to develop.

## Direct push policy

For main and develop, do not allow bypass of the protection rules. The human repository owner is the only intended promotion authority.

For ai/integration, require pull requests as above so AI work is still auditable even though no human approval is required at that stage.

## Merge strategy

Because the repository disables merge commits and the protected branches require linear history, protected-branch merges are effectively squash or rebase. Keep the repository setting and ruleset consistent with that policy.

Prefer squash merges for temporary work PRs.

For every human-controlled promotion, the AI must prepare the merge title and merge description before the human merges. The description should include:

- release/integration scope;
- notable behavior and schema changes;
- CI status;
- security-impact summary;
- known limitations and follow-up work;
- rollback considerations when relevant.

The AI must not execute ai/integration → develop or develop → main.

### ai/integration → develop

Suggested title:

~~~text
refactor: promote professional hardening to develop
~~~

The description should identify the integrated scope, validation status, known limitations, and explicitly state that the merge is a human-controlled promotion.

### develop → main

Suggested title:

~~~text
release: promote develop to main
~~~

The description should identify the release scope, validation status, database/security impact, known limitations, and rollback considerations. The merge is human-controlled.

## Automatic branch deletion

Enable:

**Settings → General → Pull Requests → Automatically delete head branches**

GitHub can automatically remove merged temporary branches. This does not replace cleanup of already-existing stale branches. GitHub documents this setting here:
https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/configuring-pull-request-merges/managing-the-automatic-deletion-of-branches

## Branch cleanup policy

Keep only:

- main
- develop
- ai/integration
- active temporary work branches
- active Dependabot branches/PRs

After a temporary PR is merged or closed, delete its branch.

## Dependabot

Dependabot targets develop, not main, so dependency updates enter the human-owned integration stream first.

Existing Dependabot PRs have already been retargeted to develop. Keep those branches/PRs while they represent active dependency updates. After develop receives the current ai/integration state, re-run or rebase the Dependabot PRs as needed and close only superseded or intentionally rejected updates.

## Verification checklist

After the GitHub UI changes:

1. Open Settings → Rules → Rulesets or Settings → Branches and verify the three branch policies.
2. Confirm main, develop, and ai/integration are covered by protection/rulesets.
3. Open a test PR into develop and verify test + secret-scan are required.
4. Verify direct pushes to main and develop are rejected.
5. Verify a temporary PR can be merged into ai/integration after required checks pass.
6. Verify merged temporary branches are automatically deleted.

GitHub rulesets can target branch patterns and require PRs, status checks, review rules, update/deletion restrictions, and bypass actors.

## AI review standard

Repository-wide review work must follow `docs/AI_REVIEW_STANDARD.md` and must inspect the exact requested branch/commit rather than the repository default branch.
