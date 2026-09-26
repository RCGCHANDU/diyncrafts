# deploy/

Deployment definitions for a single Docker host (staging and production). Start with
[docs/deployment/ARCHITECTURE.md](../docs/deployment/ARCHITECTURE.md); procedures are in
[RUNBOOK.md](../docs/deployment/RUNBOOK.md).

| Path | What it is |
|---|---|
| `compose/compose.yaml` | Application stack: Caddy edge, frontend, `api-blue`/`api-green`, `worker-blue`/`worker-green` |
| `compose/compose.stateful.yaml` | Optional on-host MySQL, RabbitMQ (STOMP), Elasticsearch, as a separate Compose project |
| `compose/*.env.example` | Host settings (`stack.env`) and non-secret Spring settings (`backend.env`) |
| `caddy/Caddyfile` | Edge: HTTPS, routing, upload limits, timeouts, blue/green health checks |
| `stateful/rabbitmq/` | Broker plugins and `consumer_timeout` for the on-host broker |
| `bin/diyncrafts-deploy` | Blue/green deploy, rollback and status (SSH forced command for CI) |
| `bin/smoke-test.sh` | Read-only post-deploy checks through the public edge |
| `bin/backup-mysql.sh`, `bin/restore-test.sh` | Logical backup, and a restore test into a throwaway container |
| `host/bootstrap.sh` | One-time host preparation (MANUAL OPERATOR ACTION) |
| `monitoring/` | Prometheus scrape config, alert rules and their unit tests |
| `test/` | Test-only configuration for running the whole stack on one machine |

The repository root's `docker-compose.yml` is for local development only.
