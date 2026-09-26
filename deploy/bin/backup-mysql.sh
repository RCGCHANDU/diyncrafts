#!/usr/bin/env bash
# Logical backup of the DIYnCrafts MySQL database (consistent snapshot, no table locks).
#
#   deploy/bin/backup-mysql.sh                 # database from compose.stateful.yaml on this host
#   MYSQL_HOST=db.internal deploy/bin/backup-mysql.sh
#                                              # any reachable MySQL, e.g. a managed instance
#
# Writes $BACKUP_DIR/diyncrafts-<UTC timestamp>.sql.gz and a .sha256 next to it, and deletes local
# backups older than $RETENTION_DAYS. Copying backups off the host (object storage with versioning or
# object lock) is required for disaster recovery and is configured by the operator.
# A managed database's own automated backups / point-in-time recovery remain the primary mechanism.
# Schedule it daily (cron or a systemd timer) and run restore-test.sh on the result regularly.
set -euo pipefail

BACKUP_DIR=${BACKUP_DIR:-/var/backups/diyncrafts}
RETENTION_DAYS=${RETENTION_DAYS:-14}
SECRETS_DIR=${SECRETS_DIR:-/etc/diyncrafts/secrets}
MYSQL_DATABASE=${MYSQL_DATABASE:-diyncraftshub}
MYSQL_IMAGE=${MYSQL_IMAGE:-mysql:8.4@sha256:0744ee5ef89ce6ccfa13de3e579fe6b9e27f93dd70da9c06d2c908b1b193fb8d}
MYSQL_CONTAINER=${MYSQL_CONTAINER:-diyncrafts-data-mysql-1}

stamp=$(date -u +%Y%m%dT%H%M%SZ)
target="$BACKUP_DIR/diyncrafts-$stamp.sql.gz"
tmp="$target.partial"
trap 'rm -f "$tmp"' EXIT
umask 077

dump_args=(--single-transaction --quick --routines --triggers --events --set-gtid-purged=OFF
  --no-tablespaces --databases "$MYSQL_DATABASE")

if [[ -z ${MYSQL_HOST:-} ]]; then
  # On-host database: dump inside the container, with the root password file mounted there.
  docker exec "$MYSQL_CONTAINER" sh -c \
    'MYSQL_PWD="$(cat /run/secrets/mysql/root-password)" exec mysqldump -uroot "$@"' mysqldump "${dump_args[@]}" |
    gzip -9 >"$tmp"
else
  # Remote database: the application user needs SELECT, SHOW VIEW, TRIGGER, EVENT and PROCESS
  # (or use a dedicated backup user and point MYSQL_PASSWORD_FILE at its password).
  password_file=${MYSQL_PASSWORD_FILE:-$SECRETS_DIR/backend/spring.datasource.password}
  docker run --rm -i --network "${MYSQL_NETWORK:-diyncrafts}" \
    -v "$password_file:/run/secrets/password:ro" "$MYSQL_IMAGE" \
    sh -c 'MYSQL_PWD="$(cat /run/secrets/password)" exec mysqldump "$@"' mysqldump \
    -h "$MYSQL_HOST" -P "${MYSQL_PORT:-3306}" -u "${MYSQL_USER:-diyncrafts}" \
    ${MYSQL_SSL_MODE:+--ssl-mode="$MYSQL_SSL_MODE"} "${dump_args[@]}" |
    gzip -9 >"$tmp"
fi

# A dump that stopped early has no completion marker.
gzip -dc "$tmp" | tail -n 1 | grep -q '^-- Dump completed' || { echo "backup incomplete" >&2; exit 1; }
mv "$tmp" "$target"
(cd "$BACKUP_DIR" && sha256sum "$(basename "$target")" >"$(basename "$target").sha256")
find "$BACKUP_DIR" -maxdepth 1 -name 'diyncrafts-*.sql.gz*' -mtime +"$RETENTION_DAYS" -delete
echo "$target"
