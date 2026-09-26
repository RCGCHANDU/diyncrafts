#!/usr/bin/env bash
# Proves that a backup can be restored, without touching any real database.
#
#   deploy/bin/restore-test.sh /var/backups/diyncrafts/diyncrafts-<stamp>.sql.gz
#
# Loads the dump into a throwaway MySQL container (no network, no published ports), then checks the
# Flyway history and the core tables, prints row counts, and removes the container. Exit code 0 means
# the backup is usable. Record the result (date, backup, duration) in the operations log.
set -euo pipefail

backup=${1:?usage: restore-test.sh <backup.sql.gz>}
MYSQL_IMAGE=${MYSQL_IMAGE:-mysql:8.4@sha256:0744ee5ef89ce6ccfa13de3e579fe6b9e27f93dd70da9c06d2c908b1b193fb8d}
MYSQL_DATABASE=${MYSQL_DATABASE:-diyncraftshub}
name="diyncrafts-restore-test-$$"
started=$SECONDS

[[ -f $backup ]] || { echo "no such file: $backup" >&2; exit 1; }
if [[ -f $backup.sha256 ]]; then
  (cd "$(dirname "$backup")" && sha256sum -c --quiet "$(basename "$backup").sha256")
  echo "checksum OK"
fi

cleanup() { docker rm -f -v "$name" >/dev/null 2>&1 || true; }
trap cleanup EXIT

docker run -d --name "$name" --network none --tmpfs /var/lib/mysql:size=4g \
  -e MYSQL_RANDOM_ROOT_PASSWORD=yes -e MYSQL_ROOT_HOST=localhost "$MYSQL_IMAGE" >/dev/null
# The random root password is only printed in the log; use the socket with auth via the log value.
for _ in $(seq 1 90); do
  password=$(docker logs "$name" 2>&1 | sed -n 's/.*GENERATED ROOT PASSWORD: //p' | head -n 1)
  if [[ -n $password ]] && docker exec -e MYSQL_PWD="$password" "$name" mysql -uroot -e 'SELECT 1' >/dev/null 2>&1; then
    break
  fi
  sleep 2
done
[[ -n ${password:-} ]] || { echo "throwaway MySQL did not start" >&2; exit 1; }

sql() { docker exec -i -e MYSQL_PWD="$password" "$name" mysql -uroot --batch --skip-column-names "$@"; }

echo "restoring $backup"
gzip -dc "$backup" | sql
version=$(sql "$MYSQL_DATABASE" -e "SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success = 1")
failed=$(sql "$MYSQL_DATABASE" -e "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 0")
echo "schema version: V$version (failed migrations: $failed)"
[[ $failed == 0 ]] || { echo "the backup contains failed migrations" >&2; exit 1; }

for table in user_account video guide category task; do
  count=$(sql "$MYSQL_DATABASE" -e "SELECT COUNT(*) FROM \`$table\`") || { echo "missing table $table" >&2; exit 1; }
  printf '%-12s %s rows\n' "$table" "$count"
done
echo "restore test passed in $((SECONDS - started))s"
