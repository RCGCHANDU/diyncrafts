# Release checklist

Use this for every production promotion. Staging deploys are automatic and do not need it.

## Before merging to `main`

- [ ] CI is green on the pull request: tests, deployment config checks, image build and scan.
- [ ] Database migrations are **backward compatible** (expand only; see RUNBOOK §4). The previous
      release must run against the new schema, because a rollback does not undo migrations.
- [ ] Transcoding message format and task statuses are unchanged, or readable by both versions.
- [ ] New configuration has a default, or is documented in ENVIRONMENTS.md and added to
      `backend.env` on staging **and** production before the deploy.
- [ ] No secret, token or real hostname in the diff. `VITE_*` values are public.
- [ ] A frontend change that needs a new API is released after the backend that provides it.

## Staging

- [ ] The **Deploy** workflow ran for this commit and its smoke tests passed.
- [ ] `diyncrafts-deploy status` on staging shows the expected digests and revision.
- [ ] Manual check of the changed feature. For upload or transcoding changes: upload a short
      video, watch live progress, play it back.
- [ ] No new errors in staging logs; no alert firing.

## Production promotion

- [ ] Time chosen with someone available to watch it; not right before a weekend without cover.
- [ ] A recent backup exists and the last restore test passed (RUNBOOK §9). For risky migrations,
      take an extra backup or snapshot now.
- [ ] Run **Deploy** → `environment: production`, `action: promote`, `component: both`.
      The approver checks that the digests in the plan match staging.
- [ ] Smoke tests pass in the workflow. If they fail, the workflow rolls back automatically.
- [ ] Watch for 30 minutes: 5xx rate, latency, transcoding failures, queue wait.
- [ ] Record the release (digests, time, approver) in the team's release log. The workflow summary
      contains the digests.

## If something is wrong

- [ ] `action: rollback` (or `diyncrafts-deploy rollback backend|frontend` on the host). Takes
      about a minute. Schema changes stay; see RUNBOOK §3.
- [ ] Write down what happened while it is fresh. Fix forward through staging.
