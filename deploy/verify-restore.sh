#!/usr/bin/env sh
set -eu

BACKUP_FILE=""
ENV_FILE=".env"
COMPOSE_FILE="docker-compose.yml"
RESTORE_DATABASE=""
MINIMUM_TABLE_COUNT="1"
KEEP_DATABASE="false"

show_help() {
  cat <<'HELP'
Verify that a MySQL backup can be restored into a temporary database without touching production data.

Usage:
  ./verify-restore.sh --backup-file ./runtime-backups/zhubao_erp_mysql_20260707-090000.sql

Options:
  --backup-file PATH       Backup .sql file exported by backup-mysql.sh.
  --env-file PATH          Compose env file. Default: .env
  --compose-file PATH      Compose file. Default: docker-compose.yml
  --restore-database NAME  Optional temporary database name.
  --minimum-table-count N  Minimum restored table count. Default: 1
  --keep-database          Keep the temporary database for manual inspection.
  -h, --help               Show this help.
HELP
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --backup-file)
      BACKUP_FILE="$2"
      shift 2
      ;;
    --env-file)
      ENV_FILE="$2"
      shift 2
      ;;
    --compose-file)
      COMPOSE_FILE="$2"
      shift 2
      ;;
    --restore-database)
      RESTORE_DATABASE="$2"
      shift 2
      ;;
    --minimum-table-count)
      MINIMUM_TABLE_COUNT="$2"
      shift 2
      ;;
    --keep-database)
      KEEP_DATABASE="true"
      shift
      ;;
    -h|--help)
      show_help
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 2
      ;;
  esac
done

cd "$(dirname "$0")"

assert_safe_name() {
  name="$1"
  label="$2"
  case "$name" in
    *[!A-Za-z0-9_]*|"")
      echo "$label contains unsafe characters. Allowed: A-Z a-z 0-9 _" >&2
      exit 2
      ;;
  esac
}

case "$MINIMUM_TABLE_COUNT" in
  *[!0-9]*|"")
    echo "Minimum table count must be a positive integer." >&2
    exit 2
    ;;
esac
if [ "$MINIMUM_TABLE_COUNT" -lt 1 ]; then
  echo "Minimum table count must be greater than 0." >&2
  exit 2
fi

if [ -z "$BACKUP_FILE" ]; then
  echo "Missing required argument: --backup-file" >&2
  exit 2
fi
if [ ! -f "$BACKUP_FILE" ]; then
  echo "Backup file not found: $BACKUP_FILE" >&2
  exit 1
fi

backup_path="$BACKUP_FILE"
sha_path="$backup_path.sha256"
if [ -f "$sha_path" ]; then
  expected_hash="$(awk '{print tolower($1)}' "$sha_path")"
  actual_hash="$(sha256sum "$backup_path" | awk '{print tolower($1)}')"
  if [ "$expected_hash" != "$actual_hash" ]; then
    echo "Backup checksum mismatch: $backup_path" >&2
    exit 1
  fi
  echo "Backup checksum verified: $backup_path"
else
  echo "Backup checksum sidecar not found; continuing without checksum verification."
fi

timestamp="$(date +%Y%m%d_%H%M%S)"
if [ -z "$RESTORE_DATABASE" ]; then
  RESTORE_DATABASE="zhubao_restore_check_$timestamp"
fi
assert_safe_name "$RESTORE_DATABASE" "RestoreDatabase"

remote_sql="/tmp/$RESTORE_DATABASE.sql"
echo "== Copy backup into MySQL container =="
docker cp "$backup_path" "zhubao-erp-mysql:$remote_sql"

echo "== Restore backup into temporary database: $RESTORE_DATABASE =="
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T \
  -e "RESTORE_DB=$RESTORE_DATABASE" \
  -e "RESTORE_SQL=$remote_sql" \
  -e "MINIMUM_TABLE_COUNT=$MINIMUM_TABLE_COUNT" \
  -e "KEEP_DATABASE=$KEEP_DATABASE" \
  mysql sh -c '
set -eu
mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "DROP DATABASE IF EXISTS \`$RESTORE_DB\`; CREATE DATABASE \`$RESTORE_DB\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$RESTORE_DB" < "$RESTORE_SQL"
table_count="$(mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N "$RESTORE_DB" -e "SHOW TABLES;" | wc -l | tr -d " ")"
echo "Restored table count: $table_count"
if [ "$table_count" -lt "$MINIMUM_TABLE_COUNT" ]; then
  echo "Restored table count is below expected minimum." >&2
  exit 1
fi
if [ "$KEEP_DATABASE" != "true" ]; then
  mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "DROP DATABASE IF EXISTS \`$RESTORE_DB\`;"
  rm -f "$RESTORE_SQL"
fi
'

echo "Restore verification passed: $RESTORE_DATABASE"
if [ "$KEEP_DATABASE" = "true" ]; then
  echo "Temporary restore database was kept for inspection."
else
  echo "Temporary restore database was removed."
fi
