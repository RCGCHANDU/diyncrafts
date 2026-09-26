# Environments and configuration

Four environments use the same code. The two deployed ones (staging and production) run the **same
image digests**: production only ever receives digests that are already running in staging.

| | Local | CI | Staging | Production |
|---|---|---|---|---|
| Purpose | Development | Tests, image build, scan, publish | Release candidate verification | Users |
| Backend | `./mvnw spring-boot:run` (profile `local`) | `./mvnw -B verify` (Testcontainers) | Image, roles `api` + `worker` | Same digest as staging |
| Frontend | `npm run dev` (Vite proxy) | `npm ci`, lint, test, build, Playwright | Image (or `dist/` on static hosting) | Same digest as staging |
| Dependencies | `docker compose up -d` (repo root, **not for production**) | Testcontainers / MSW | External or `compose.stateful.yaml` | Managed services preferred |
| Deploy | — | Publishes `sha-<commit>` images to GHCR on `main` | Automatic after CI on `main` | Manual `workflow_dispatch`, approved |
| Data | Throwaway | Throwaway | Test data only (never a copy of production personal data) | Real |

## Configuration model

Configuration comes from the environment, never from the image:

| Kind | Where it lives | Example |
|---|---|---|
| Defaults | `src/main/resources/application*.yml` (in the image) | timeouts, renditions |
| Role | `SPRING_PROFILES_ACTIVE=prod,api` or `prod,worker` (set by `compose.yaml`) | consumer on/off, drain time |
| Non-secret environment settings | `/etc/diyncrafts/backend.env` ([example](../../deploy/compose/backend.env.example)) | database URL, bucket, CORS origin |
| Host settings | `/etc/diyncrafts/stack.env` ([example](../../deploy/compose/stack.env.example)) | domain, allowed image repositories, limits |
| Secrets | `/etc/diyncrafts/secrets/backend/<property>`: one file per property, read by Spring's `configtree` | `app.jwt.secret` |
| Deployed digests | `/var/lib/diyncrafts-deploy/images.env`, written only by `diyncrafts-deploy` | active colour, previous digests |
| Frontend (build time, **public**) | `VITE_API_BASE_URL`, `VITE_WS_URL`, empty = same origin | — |
| Frontend (run time, public) | `MEDIA_ORIGIN`, `API_ORIGIN` for the Content-Security-Policy | `https://media.example.com` |

Everything in a `VITE_*` variable is compiled into JavaScript that every visitor downloads. It must
never contain a secret. The frontend image is built with empty values, so one artifact serves every
environment behind the edge proxy.

### Backend settings

| Variable | Required | Notes |
|---|---|---|
| `SPRING_DATASOURCE_URL`, `_USERNAME` | yes | MySQL 8.4. Use TLS for a managed database (`sslMode=VERIFY_IDENTITY`). |
| `SPRING_RABBITMQ_HOST`, `_PORT`, `_USERNAME`, `_VIRTUAL_HOST`, `_SSL_ENABLED` | yes | The broker needs the `rabbitmq_stomp` plugin, and a `consumer_timeout` above 25 minutes. |
| `APP_WEBSOCKET_RELAY_ENABLED`, `_HOST`, `_PORT` | yes (set to `true` by compose) | Live progress when API and worker are separate processes. |
| `SPRING_ELASTICSEARCH_URIS`, `_USERNAME` | yes | `https://` for anything off-host; certificate verification stays on. |
| `APP_STORAGE_BUCKET`, `_REGION`, `_ENDPOINT`, `_PATH_STYLE_ACCESS`, `_PUBLIC_BASE_URL` | bucket, region | Leave `_ENDPOINT` unset for AWS S3. Media is read by browsers from `_PUBLIC_BASE_URL`. |
| `APP_CORS_ALLOWED_ORIGINS` | yes | The site origin, e.g. `https://www.example.com`. Also the WebSocket origin allow-list. Never `*`. |
| `APP_TRANSCODING_ENCODER` | no | `libx264` (default) or `h264_nvenc` on NVIDIA hosts. |
| `APP_TRANSCODING_MIN_FREE_SPACE` | no | Default `2GB`. Uploads are refused with 503 below it. |
| `APP_ENVIRONMENT` | yes (compose) | `staging` or `production`; in every log line. |

### Secrets

| Secret (file name in `secrets/backend/`) | Used by | Rotation |
|---|---|---|
| `app.jwt.secret` | API: signs 1-hour session tokens | Rotating it signs every user out. See RUNBOOK "Rotate secrets". |
| `spring.datasource.password` | API, worker | Add a new database user or password first, then switch and deploy, then revoke the old one. |
| `spring.rabbitmq.password` | API, worker, STOMP relay | Same pattern. |
| `spring.elasticsearch.password` | API, worker | Same pattern. |
| `app.bootstrap-admin.password` | API, first start only | Remove the file after the first administrator exists. |
| `aws.env` (in `secrets/`, optional) | API, worker | Only when no instance role or workload identity exists. |

Files are owned by `root:10001` with mode `0440`, so only the application's UID can read them. They
never appear in images, Compose files, workflow YAML or logs. Where a cloud secret manager exists,
render these files from it (for example with a systemd unit that runs before `diyncrafts-deploy`),
rather than copying values by hand.

## GitHub configuration (MANUAL OPERATOR ACTION)

Workflows reference these names; the values are created by an administrator in the repository's
**Settings → Environments** and are never committed.

| Environment | Kind | Name | Value |
|---|---|---|---|
| `staging` | secret | `DEPLOY_SSH_KEY` | Private key whose public half is in the staging host's `authorized_keys` **with the forced command** |
| `staging` | secret | `DEPLOY_SSH_KNOWN_HOSTS` | Output of `ssh-keyscan -t ed25519 <host>`, verified out of band |
| `staging` | variable | `DEPLOY_HOST`, `DEPLOY_USER` | Host name, `diyncrafts-deploy` |
| `staging` | variable | `PUBLIC_URL` | `https://<staging domain>` (smoke tests) |
| `production` | same four | | Production host and a **different** key |
| `production` | protection | required reviewers | At least one person other than the author; restrict to `main` |

The deploy workflows live in the backend repository (which also holds `deploy/`). The frontend's
workflow publishes its image and then requests a staging deploy there. That cross-repository
trigger needs a fine-grained token limited to `Actions: write` on the backend repository, stored as
the frontend secret `BACKEND_DISPATCH_TOKEN`. Without it, staging picks up new frontend images on
the next backend deploy, or through a manual run of the backend's deploy workflow.

Images in GHCR are private by default for private repositories. Either make the two packages
public, or log the deploy user in once with a token limited to `read:packages`.

**Letting CI create the packages (one-time, MANUAL OPERATOR ACTION).** The first publish from `main`
fails with `denied: installation not allowed to Create organization package` when the account's
settings do not let GitHub Actions create packages. Choose one:

- In the account's or organization's **Settings → Packages**, allow package creation for the
  visibility you want. Then re-run the failed CI run on `main`.
- Or create each package once by hand. Push any image to `ghcr.io/rcgchandu/diyncrafts` and to
  `ghcr.io/rcgchandu/diyncrafts-frontend` with a personal token that has `write:packages`. Then, in
  each package's **Package settings → Manage Actions access**, add its repository with the
  **Write** role, and re-run CI.

After that, `GITHUB_TOKEN` (granted `packages: write` only in the publish job) can push new
versions. No long-lived token is stored in the repository.

### OIDC instead of static cloud keys

The reference target (a VM reached over SSH) needs no cloud credentials in GitHub. When a cloud
provider is chosen, do not add long-lived access keys to GitHub. Use the provider's GitHub OIDC
federation:

- The workflow requests `permissions: id-token: write` in the deploy job only.
- The cloud role trusts `token.actions.githubusercontent.com` and is restricted by the `sub` claim to
  `repo:RCGCHANDU/diyncrafts:environment:production` (and a separate role for `:environment:staging`).
- The role allows only what deploying needs (for example pulling from the registry and updating one
  service), nothing account-wide.

The same applies to the application's own cloud access (S3): an instance role or workload identity,
not keys in `aws.env`. On EC2, containers reach the instance role only if the metadata hop limit is 2
(IMDSv2).

## Local development is unchanged

- `docker compose up -d` in the backend repository still starts local dependencies only. That file is
  labelled *not for production*.
- `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` still runs the whole application in one
  process (API and consumer). No role profile is active, so both run.
- `npm run dev` in the frontend still proxies `/api` and `/ws` to `http://localhost:8080`.

To try the production images locally, see "Testing the deployment stack" in
[RUNBOOK.md](RUNBOOK.md).
