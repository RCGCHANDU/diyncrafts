#!/usr/bin/env bash
# One-time (idempotent) preparation of a Docker host for DIYnCrafts. MANUAL OPERATOR ACTION: run as
# root on the target host from a checkout of this repository:
#
#   sudo deploy/host/bootstrap.sh                   # application stack only (external databases)
#   sudo deploy/host/bootstrap.sh --with-stateful   # also prepare compose.stateful.yaml
#
# It never overwrites existing configuration or secrets. It creates:
#   /opt/diyncrafts                 copy of deploy/ (compose files, Caddyfile, scripts)
#   /etc/diyncrafts                 stack.env, backend.env (from the examples; edit them)
#   /etc/diyncrafts/secrets         secret files (generated only when missing)
#   /var/lib/diyncrafts/work        upload/transcoding scratch (UID 10001)
#   /var/lib/diyncrafts-edge        Caddy certificates and state (UID 10002)
#   /var/lib/diyncrafts-deploy      deploy state: active digests, history
#   user diyncrafts-deploy          runs deploys (member of the docker group)
set -euo pipefail

SOURCE_DIR=$(cd "$(dirname "$0")/.." && pwd)
DIYNCRAFTS_HOME=${DIYNCRAFTS_HOME:-/opt/diyncrafts}
DIYNCRAFTS_ETC=${DIYNCRAFTS_ETC:-/etc/diyncrafts}
DIYNCRAFTS_STATE=${DIYNCRAFTS_STATE:-/var/lib/diyncrafts-deploy}
DATA_DIR=${DATA_DIR:-/var/lib/diyncrafts}
EDGE_DIR=${EDGE_DIR:-/var/lib/diyncrafts-edge}
BACKUP_DIR=${BACKUP_DIR:-/var/backups/diyncrafts}
SECRETS_DIR="$DIYNCRAFTS_ETC/secrets"
DEPLOY_USER=${DEPLOY_USER:-diyncrafts-deploy}
APP_UID=10001
EDGE_UID=10002
# UIDs of the official images' service users (compose.stateful.yaml).
MYSQL_UID=999
RABBITMQ_UID=999
ELASTICSEARCH_UID=1000

with_stateful=false
[[ ${1:-} == --with-stateful ]] && with_stateful=true

[[ $(id -u) == 0 ]] || { echo "run as root" >&2; exit 1; }
command -v docker >/dev/null || { echo "Docker Engine is required" >&2; exit 1; }
docker compose version >/dev/null || { echo "the Docker Compose plugin (v2.24+) is required" >&2; exit 1; }

say() { printf '==> %s\n' "$*"; }

# secret FILE OWNER MODE [VALUE]: creates FILE with a random value unless it exists.
secret() {
  local file=$1 owner=$2 mode=$3 value=${4:-}
  if [[ ! -s $file ]]; then
    [[ -n $value ]] || value=$(openssl rand -base64 48 | tr -d '\n/+=' | cut -c1-48)
    (umask 077; printf '%s' "$value" >"$file")
    say "generated $file"
  fi
  chown "$owner" "$file"
  chmod "$mode" "$file"
}

say "deploy user"
id "$DEPLOY_USER" >/dev/null 2>&1 || useradd --system --create-home --shell /bin/bash "$DEPLOY_USER"
usermod -aG docker "$DEPLOY_USER"

say "files in $DIYNCRAFTS_HOME"
install -d -m 0755 "$DIYNCRAFTS_HOME"
cp -R "$SOURCE_DIR/compose" "$SOURCE_DIR/caddy" "$SOURCE_DIR/stateful" "$SOURCE_DIR/bin" "$SOURCE_DIR/monitoring" "$DIYNCRAFTS_HOME/"
chmod 0755 "$DIYNCRAFTS_HOME"/bin/*
ln -sf "$DIYNCRAFTS_HOME/bin/diyncrafts-deploy" /usr/local/bin/diyncrafts-deploy

say "configuration in $DIYNCRAFTS_ETC"
install -d -m 0750 -o root -g "$DEPLOY_USER" "$DIYNCRAFTS_ETC"
for f in stack.env backend.env; do
  if [[ ! -f $DIYNCRAFTS_ETC/$f ]]; then
    install -m 0640 -o root -g "$DEPLOY_USER" "$SOURCE_DIR/compose/$f.example" "$DIYNCRAFTS_ETC/$f"
    say "created $DIYNCRAFTS_ETC/$f from the example: EDIT IT"
  fi
done

say "secrets in $SECRETS_DIR"
# Traversable but not listable; each service can read only its own files.
install -d -m 0711 -o root -g root "$SECRETS_DIR"
install -d -m 0750 -o root -g "$APP_UID" "$SECRETS_DIR/backend"
secret "$SECRETS_DIR/backend/app.jwt.secret" "root:$APP_UID" 0440

if $with_stateful; then
  install -d -m 0711 -o root -g root "$SECRETS_DIR/mysql" "$SECRETS_DIR/rabbitmq" "$SECRETS_DIR/elasticsearch"
  secret "$SECRETS_DIR/mysql/root-password" "$MYSQL_UID:root" 0400
  secret "$SECRETS_DIR/mysql/app-password" "$MYSQL_UID:root" 0400
  secret "$SECRETS_DIR/backend/spring.datasource.password" "root:$APP_UID" 0440 "$(cat "$SECRETS_DIR/mysql/app-password")"
  secret "$SECRETS_DIR/backend/spring.rabbitmq.password" "root:$APP_UID" 0440
  secret "$SECRETS_DIR/rabbitmq/credentials.conf" "$RABBITMQ_UID:root" 0400 \
    "$(printf 'default_user = diyncrafts\ndefault_pass = %s\n' "$(cat "$SECRETS_DIR/backend/spring.rabbitmq.password")")"
  secret "$SECRETS_DIR/elasticsearch/elastic-password" "$ELASTICSEARCH_UID:root" 0400
  secret "$SECRETS_DIR/backend/spring.elasticsearch.password" "root:$APP_UID" 0440 "$(cat "$SECRETS_DIR/elasticsearch/elastic-password")"
  # Elasticsearch refuses to start with a lower limit.
  echo 'vm.max_map_count = 262144' >/etc/sysctl.d/90-diyncrafts-elasticsearch.conf
  sysctl -q -w vm.max_map_count=262144 || say "could not set vm.max_map_count (set it on the host)"
fi

say "data directories"
install -d -m 0755 "$DATA_DIR"
install -d -m 0750 -o "$APP_UID" -g "$APP_UID" "$DATA_DIR/work"
install -d -m 0750 -o "$EDGE_UID" -g "$EDGE_UID" "$EDGE_DIR" "$EDGE_DIR/data" "$EDGE_DIR/config"
install -d -m 0750 -o "$DEPLOY_USER" -g "$DEPLOY_USER" "$DIYNCRAFTS_STATE"
install -d -m 0750 -o root -g "$DEPLOY_USER" "$BACKUP_DIR"

say "docker network"
docker network inspect diyncrafts >/dev/null 2>&1 || docker network create diyncrafts >/dev/null

cat <<EOF

Host prepared. Remaining MANUAL OPERATOR ACTIONS:
  1. Edit $DIYNCRAFTS_ETC/stack.env and $DIYNCRAFTS_ETC/backend.env (domain, database, broker,
     search, bucket, CORS origin).
  2. Put the remaining secrets in $SECRETS_DIR/backend/ (one file per property, owner root:$APP_UID,
     mode 0440), e.g. spring.datasource.password for a managed database.
  3. Let the deploy user pull images (skip for public packages), with a read:packages token:
       sudo -u $DEPLOY_USER docker login ghcr.io -u <github-user>
  4. Authorize the CI deploy key (forced command, no shell):
       echo 'command="/usr/local/bin/diyncrafts-deploy --ssh",restrict <public key>' \\
         >> ~$DEPLOY_USER/.ssh/authorized_keys
  5. Firewall: allow inbound 22 (restricted), 80 and 443 only.
  6. Point DNS for SITE_ADDRESS at this host, then start the edge:
       sudo -u $DEPLOY_USER diyncrafts-deploy edge
EOF
