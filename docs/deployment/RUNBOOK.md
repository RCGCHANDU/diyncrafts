# Operations runbook

Procedures for the reference deployment: one Docker host running `deploy/compose/compose.yaml`, with
the deploy script `diyncrafts-deploy`. Architecture and decisions are in
[ARCHITECTURE.md](ARCHITECTURE.md); configuration in [ENVIRONMENTS.md](ENVIRONMENTS.md).

Steps marked **MANUAL OPERATOR ACTION** change production or data. Carry them out only with the
authorization your team requires, and never from automation that has not been reviewed.

Conventions:

```bash
ssh <host>                                   # an operator account, not the CI deploy key
sudo -u diyncrafts-deploy -i                 # the deploy user (docker group)
dc() { docker compose --project-directory /opt/diyncrafts/compose -f /opt/diyncrafts/compose/compose.yaml \
  --env-file /etc/diyncrafts/stack.env --env-file /var/lib/diyncrafts-deploy/images.env "$@"; }
```

## Contents

1. [First-time setup](#1-first-time-setup)
2. [Deploy](#2-deploy)
3. [Rollback](#3-rollback)
4. [Database migrations](#4-database-migrations)
5. [Workers and transcoding jobs](#5-workers-and-transcoding-jobs)
6. [Search index](#6-search-index)
7. [Disk full](#7-disk-full)
8. [Rotate secrets](#8-rotate-secrets)
9. [Backups and restore](#9-backups-and-restore)
10. [Disaster recovery](#10-disaster-recovery)
11. [Observability](#11-observability)
12. [Testing the deployment stack](#12-testing-the-deployment-stack)

## 1. First-time setup

**MANUAL OPERATOR ACTION**. Nothing here is automated, because it creates infrastructure.

1. Provision a Linux VM. Size it from expected transcoding load; as a starting point, 4 vCPU and 8 GB
   of RAM with external data services. Give it a separate data disk for `/var/lib/diyncrafts`
   (upload scratch), sized for concurrent uploads plus their outputs (at least 50 GB).
2. Install Docker Engine and the Compose plugin (v2.24 or newer) from Docker's repository. Enable
   unattended security updates.
3. Provision the stateful services: managed MySQL 8.4 (automated backups and PITR on), RabbitMQ 4.x
   with `rabbitmq_stomp` and `consumer_timeout` ≥ 1 h, Elasticsearch 9.x, and an S3 bucket with a CDN.
   The alternative for staging or a very small production is `compose.stateful.yaml`.
4. Bucket: block public writes. Allow public (or CDN) reads of `videos/`, `thumbnails/` and
   `guides/`. Add a CORS rule allowing `GET` and `HEAD` from the site origin only. Enable versioning.
5. From a checkout of the release tag: `sudo deploy/host/bootstrap.sh` (add `--with-stateful` for the
   on-host services). Follow the steps it prints: configuration files, secrets, registry login, CI
   key and firewall.
6. Point DNS at the host, then start the edge: `diyncrafts-deploy edge`.
7. Run the first deploy from GitHub Actions (staging), or by hand with digests from the CI summary:
   `diyncrafts-deploy deploy backend ghcr.io/rcgchandu/diyncrafts@sha256:…`, then the frontend.
8. Check: `diyncrafts-deploy status` and `deploy/bin/smoke-test.sh https://<domain>`.

Changes to `deploy/` (Compose files, Caddyfile) are reviewed in pull requests. They reach the host
by copying the reviewed tag's `deploy/` to `/opt/diyncrafts` (re-running `bootstrap.sh` does that
and never overwrites configuration or secrets). After a Caddyfile change, run
`diyncrafts-deploy edge`. After a Compose change to the application services, redeploy the current
digests.

## 2. Deploy

Normal path: merge to `main`. CI builds, tests, scans and publishes images. The **Deploy** workflow
deploys them to staging and runs the smoke tests. Production is the same workflow, run by hand with
`environment: production`, and approved by a reviewer. It promotes the digests currently running in
staging.

What `diyncrafts-deploy deploy backend <digest>` does:

1. It refuses anything that is not `<allowed repository>@sha256:<digest>`.
2. It pulls the image and starts the **idle colour's API**. Flyway migrations run there before it
   reports ready.
3. It waits for Docker's health check (readiness). If that fails, it removes the new container and
   exits non-zero. The old colour never stopped serving.
4. It stops the old API colour gracefully. In-flight requests get up to 2 minutes. Caddy stops routing
   to a colour as soon as its readiness fails or connections are refused, and retries elsewhere.
5. It records the new active colour and the previous digest in `/var/lib/diyncrafts-deploy/images.env`,
   and appends to `deploy.log`.
6. It starts the new worker and waits for health. The old worker stops taking jobs and finishes its
   current one (up to 31 minutes) in the background; see `drain.log`.

Frontend deploys replace the single frontend container. Caddy retries for up to 10 seconds, so users
see no errors.

Manual deploy (for example during an incident, when Actions is unavailable), **MANUAL OPERATOR ACTION**
for production:

```bash
diyncrafts-deploy status
diyncrafts-deploy deploy backend ghcr.io/rcgchandu/diyncrafts@sha256:<digest>
deploy/bin/smoke-test.sh https://<domain>
```

Never deploy a tag such as `latest`; the script refuses it.

## 3. Rollback

```bash
diyncrafts-deploy rollback backend     # or: frontend
```

This redeploys the previous digest through the same blue/green path, which takes about a minute. The
GitHub **Deploy** workflow does the same automatically when post-deploy smoke tests fail. It can also
be run manually with `action: rollback`.

Limits:

- **Schema.** A rollback does not undo migrations. It is safe only because migrations are backward
  compatible (next section). If a release shipped a destructive migration, restore the database
  instead (section 9). That is a data-loss decision.
- **Transcoding jobs** queued by the new version are processed by the rolled-back worker. The message
  format is `TranscodingJob(taskId)` and must stay compatible across adjacent releases.

To go back further than one release, deploy an older digest explicitly (from `deploy.log` or the CI
run summaries).

## 4. Database migrations

Flyway runs in the **API role at startup** (single migrator). Workers have Flyway disabled and start
only after the new API is ready. `ddl-auto=validate` stops any process whose entities do not match
the schema.

Rules for every migration (expand/contract):

1. **Expand**: add nullable columns or new tables; never rename or drop in the same release that
   stops using them. Old and new code must both run against the new schema, because during a deploy,
   and after a rollback, the old version runs against it.
2. **Contract** in a later release, once no running version uses the old structure.
3. Keep migrations short. Long data backfills belong in application code or a separate, reviewed job.
   The API's 5-minute readiness window must cover the migration.
4. Never edit an applied migration. Flyway validation fails and the new API will not start. The old
   colour keeps serving, so the failure is safe but blocks the release.

A failed migration: the new API exits, the deploy aborts, and the old version keeps serving. MySQL DDL
is not transactional, so check `flyway_schema_history` for a row with `success = 0`. Fix it forward
with a new migration after repairing the partial change. Running `flyway repair` against production
is a **MANUAL OPERATOR ACTION**.

## 5. Workers and transcoding jobs

Job lifecycle: `QUEUED` → `PROCESSING` → `COMPLETED` or `FAILED`. The task row in MySQL is the
source of truth. The RabbitMQ message only carries the task ID. Each message is delivered at most 3
times, then goes to `diyncrafts.transcoding.dlq`.

**Restart workers** (drains the running job first):

```bash
dc restart worker-$(sed -n 's/^ACTIVE_COLOR=//p' /var/lib/diyncrafts-deploy/images.env)
```

A restart waits up to 32 minutes for the running job. To stop immediately (the job is redelivered
later and counts one delivery): `dc kill -s SIGKILL worker-<colour>`, then `dc up -d worker-<colour>`.

**Failed or stuck jobs:**

- The frontend shows `errorDetails`. The worker log has the full context: search for the `taskId`
  field in the JSON logs, e.g. `dc logs worker-blue | grep <taskId>`.
- A task stuck in `PROCESSING` longer than 50 minutes (2× the timeout) is failed automatically by
  `StaleTaskWatchdog` within 10 minutes.
- The user re-uploads; there is no automatic re-run of a failed task.

**Dead-lettered jobs** (alert `DiyncraftsDeadLetters`): look at them first. The management UI is on
the host's loopback only (`ssh -L 15672:127.0.0.1:15672 <host>`, then http://localhost:15672) for
the on-host broker; for a managed broker, use its console. Their tasks are already `FAILED`.
Purging the DLQ deletes the evidence; that is a **MANUAL OPERATOR ACTION**.

**More capacity**: raise `WORKER_CPUS` and `WORKER_MEMORY`, or move the host to a larger size. More
workers on the same host are possible by adding services, since they share the scratch volume. Workers
on other hosts need direct-to-S3 uploads first (ARCHITECTURE.md §8).

## 6. Search index

Elasticsearch is derived data. MySQL is the source of truth.

- Rebuild: sign in as an administrator, open **Admin → Search index → Re-index**, or call
  `POST /api/admin/search/reindex` with an admin token. The endpoint indexes every video from MySQL
  into the current index (`videos_v2`).
- The application never deletes indexes. Deleting an old or corrupted index to rebuild it is a
  **MANUAL OPERATOR ACTION**. Take a snapshot first if the cluster supports it.
- Elasticsearch unavailable: search endpoints fail. Uploads and playback continue. Transcoding
  retries indexing; if that keeps failing, re-index once the cluster is back.

## 7. Disk full

Alerts: `DiyncraftsScratchDiskLow` (< 15 % free) and `DiyncraftsScratchDiskCritical` (< 5 %).
Uploads are refused with 503 once accepting one would leave less than
`APP_TRANSCODING_MIN_FREE_SPACE` (2 GB) free.

1. `df -h /var/lib/diyncrafts` and `du -sh /var/lib/diyncrafts/work/* | sort -h | tail`.
2. Task directories are deleted when a task completes or fails. Directories whose task is terminal,
   and `.multipart` files older than a day, are leftovers. After checking the task status, remove
   them: `find /var/lib/diyncrafts/work -mindepth 1 -maxdepth 1 -mtime +1 ...`. This is a
   **MANUAL OPERATOR ACTION**; never delete the directory of a `QUEUED` or `PROCESSING` task.
3. Docker's own space: `docker system df`. Old images can be pruned
   (`docker image prune -a --filter until=720h`), but keep the previous release's image for rollback.
4. Container logs are capped (5 × 50 MB per container, `local` driver).

## 8. Rotate secrets

**MANUAL OPERATOR ACTION** for production. Pattern: create the new credential, switch, verify, then
revoke the old one.

- **Database, broker or search password**: create a second user (or a second password where the
  service supports it). Write the new value to `/etc/diyncrafts/secrets/backend/<property>` with the
  same owner and mode. Redeploy the current digest with `diyncrafts-deploy rollback backend` twice,
  or deploy the same digest again after `diyncrafts-deploy status`. Blue/green picks up the file.
  Verify, then revoke the old credential.
- **`app.jwt.secret`**: replacing it invalidates all sessions, so everyone signs in again. Do it at a
  quiet time, or immediately if the secret leaked.
- **CI deploy key**: add the new public key to `authorized_keys` (with the same forced command),
  update the `DEPLOY_SSH_KEY` environment secret, run a staging deploy, then remove the old key.
- **Leaked secret**: rotate first, then investigate. Check `deploy.log`, container logs and the
  GitHub audit log.

## 9. Backups and restore

| Data | Primary backup | Recovery objective (target; confirm with the business) |
|---|---|---|
| MySQL | Managed: automated daily snapshots and PITR (7+ days). On-host: `deploy/bin/backup-mysql.sh` daily, copied off-host | RPO ≤ 24 h (≤ 5 min with PITR); RTO ≤ 2 h |
| Object storage | Bucket versioning, plus replication to another region or account for DR | RPO ≈ 0 for existing media |
| Elasticsearch | None needed: rebuild from MySQL (section 6) | RTO: re-index time |
| RabbitMQ | None: only in-flight jobs; tasks are in MySQL | Users re-upload lost in-flight jobs |
| Configuration | `/etc/diyncrafts` (encrypted, off-host), and `deploy/` in Git | — |
| Caddy certificates | Re-issued automatically (keep `/var/lib/diyncrafts-edge` to avoid ACME rate limits) | minutes |

**On-host MySQL backup**: `deploy/bin/backup-mysql.sh` takes a consistent `mysqldump`, checks it
completed, writes a checksum and prunes local copies older than 14 days. Schedule it daily (systemd
timer or cron as the deploy user). Copying the files off the host (for example to a versioned bucket
with object lock) is required. Its destination and credentials are an operator decision.

**Restore test** (monthly, and after any backup change):

```bash
deploy/bin/restore-test.sh /var/backups/diyncrafts/diyncrafts-<stamp>.sql.gz
```

It loads the dump into a throwaway MySQL container with no network, checks the Flyway history and
core tables, prints row counts and removes the container. It never touches a real database. Record the
date, backup and duration.

**Restore production** is a **MANUAL OPERATOR ACTION** (data loss after the backup point):

1. Put the site in maintenance: stop the API and workers (`dc stop api-blue api-green worker-blue
   worker-green`). The frontend stays up and shows API errors.
2. Managed database: restore to a new instance at the chosen point in time, then point
   `SPRING_DATASOURCE_URL` at it. On-host: restore into a new database and switch the URL. Keep the
   damaged one for investigation.
3. Redeploy the current backend digest. Flyway applies nothing new, or the migrations after the
   backup point.
4. Re-index search (section 6). Tasks that were in flight are `FAILED` by the watchdog; users re-upload.
5. Media uploaded after the backup point exists in storage without database rows. That is harmless;
   clean it up later.

## 10. Disaster recovery

Host lost: every piece of state on it is either rebuildable (images, search, certificates) or
temporary (scratch, in-flight jobs).

1. Provision a new host (section 1, steps 1–2) and restore `/etc/diyncrafts` from its encrypted
   backup. Otherwise, recreate it from the examples and the secret store.
2. `bootstrap.sh`, then `diyncrafts-deploy edge`.
3. Deploy the last known good digests (from the GitHub **Deploy** run summaries or `deploy.log`
   backups).
4. Switch DNS to the new host (**MANUAL OPERATOR ACTION**; lower the TTL in advance).
5. Smoke test; re-index search if Elasticsearch was on the lost host.

Region lost: restore MySQL from a cross-region backup or replica, and serve media from the replicated
bucket. Then proceed as above in the other region. Practise this once before relying on it.

## 11. Observability

- **Logs**: every backend line is JSON (Elastic Common Schema). It includes `service.version` (the
  commit), `service.environment`, and `taskId` for transcoding. Caddy access logs are JSON without
  query strings, `Authorization` or cookies. No component logs tokens or passwords. Ship
  `docker logs` output with the collector of your choice (Docker `local` driver files, or switch
  the logging driver).
- **Metrics**: `/actuator/prometheus` on each backend container, reachable only on the internal
  Docker network (the edge returns 404 for `/actuator`). `deploy/monitoring/prometheus.yml` scrapes
  whichever colours are running via Docker DNS. The rules in `deploy/monitoring/alerts.yml` cover
  availability, 5xx rate, latency, scratch disk, heap, and the transcoding SLOs:
  - 90 % of jobs start within 15 minutes;
  - 95 % of jobs complete;
  - no queued work without progress for 30 minutes.

  Alert delivery (Alertmanager receivers, paging) is an operator decision.
- **Version**: `diyncrafts-deploy status` shows the digests and the running commit
  (`/actuator/info` inside the container).
- **External check**: add an uptime check of `https://<domain>/` and `/api/categories` from outside
  the host. It covers DNS, TLS expiry and the host itself, which on-host Prometheus cannot.

Key metrics:

| Metric | Meaning |
|---|---|
| `diyncrafts_transcoding_tasks{status="queued"\|"processing"}` | Current backlog (from MySQL) |
| `diyncrafts_transcoding_queue_wait_seconds` | Upload → start of processing |
| `diyncrafts_transcoding_processing_seconds{outcome}` | Processing time |
| `diyncrafts_transcoding_jobs_total{outcome}` | Completed and failed jobs |
| `disk_free_bytes{path="/var/lib/diyncrafts/work"}` | Scratch space |
| `http_server_requests_seconds{uri,status}` | API traffic, errors and latency |

## 12. Testing the deployment stack

The whole stack can run on one machine with images from a local registry. `deploy/test/` holds
test-only configuration: test `stack.env` and `backend.env`, an S3 mock and a media site. The
results of the reference run are in ARCHITECTURE.md §11. In short:

```bash
sudo deploy/host/bootstrap.sh --with-stateful
sudo install -m 0640 -g diyncrafts-deploy deploy/test/stack.env deploy/test/backend.env /etc/diyncrafts/
# plus secrets/aws.env with dummy keys and secrets/backend/app.bootstrap-admin.password
docker compose -f deploy/compose/compose.stateful.yaml --env-file /etc/diyncrafts/stack.env up -d
docker compose -f deploy/test/compose.test.yaml up -d
export DIYNCRAFTS_HOME=$PWD/deploy
deploy/bin/diyncrafts-deploy edge
deploy/bin/diyncrafts-deploy deploy frontend localhost:5000/rcgchandu/diyncrafts-frontend@sha256:…
deploy/bin/diyncrafts-deploy deploy backend  localhost:5000/rcgchandu/diyncrafts@sha256:…
CURL_CA_BUNDLE=<caddy local root + system CAs> deploy/bin/smoke-test.sh https://localhost:18443
```
