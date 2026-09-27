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

AI must never merge a PR into develop or main.

## Required GitHub protection

Configure these three branches under **Settings → Branches / Rulesets**.

### main

Require:

- pull request before merging;
- at least 1 approving human review;
- dismiss stale approvals when new commits are pushed;
- require Code Owner approval;
- require approval from someone other than the latest pusher where available;
- status checks: test and secret-scan;
- branch must be up to date before merge;
- all review conversations resolved;
- no force pushes;
- no deletion;
- enforce rules for administrators;
- linear history / squash-or-rebase only.

### develop

Use the same protection as main.

This makes develop human-owned while still allowing CI and normal pull requests to update it.

### ai/integration

Require:

- pull request before merging;
- status checks: test and secret-scan;
- branch must be up to date before merge;
- all review conversations resolved;
- no force pushes;
- no deletion.

Do not require a human approval on ai/integration if the intention is for the AI to integrate temporary branches automatically after CI passes. Human review happens on promotion to develop.

## Direct push policy

For main and develop, do not allow bypass of the protection rules. The human repository owner is the only intended promotion authority.

For ai/integration, require pull requests as above so AI work is still auditable even though no human approval is required at that stage.

## Merge strategy

Prefer squash merges for temporary work PRs.

For ai/integration → develop and develop → main, use the repository's chosen human release convention consistently. The AI should propose the merge title/body but must not execute these merges.

## Automatic branch deletion

Enable:

**Settings → General → Pull Requests → Automatically delete head branches**

GitHub can automatically remove merged temporary branches. This does not replace cleanup of already-existing stale branches. GitHub documents this setting here: https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/configuring-pull-request-merges/managing-the-automatic-deletion-of-branches

## Branch cleanup policy

Keep only:

- main
- develop
- ai/integration
- active temporary work branches
- active Dependabot branches/PRs

After a temporary PR is merged or closed, delete its branch.

## Dependabot

Dependabot targets develop, not main, so dependency updates enter the human-owned integration stream first. Existing old Dependabot PRs created against main should be closed/recreated after this policy is in place.

## Verification checklist

After the GitHub UI changes:

1. Open Settings → Rules → Rulesets or Settings → Branches and verify the three branch policies.
2. Confirm main, develop, and ai/integration are covered by protection/rulesets.
3. Open a test PR into develop and verify test + secret-scan are required.
4. Verify direct pushes to main and develop are rejected.
5. Verify a temporary PR can be merged into ai/integration after required checks pass.
6. Verify merged temporary branches are automatically deleted.

GitHub rulesets can target branch patterns and require PRs, status checks, review rules, update/deletion restrictions, and bypass actors.