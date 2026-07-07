#!/usr/bin/env sh
set -eu

BACKUP_FILE=""
ENV_FILE=".env"
COMPOSE_FILE="docker-compose.yml"
RESTORE_DATABASE=""
MINIMUM_TABLE_COUNT="1"
KEEP_DATABASE="false"
OPERATOR_NAME=""

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
  --operator NAME          Optional operator name written to the structured restore drill record.
  --minimum-table-count N  Minimum restored table count. Default: 1
  --keep-database          Keep the temporary database for manual inspection.
  On success, writes verify-restore-<timestamp>.log/json next to the backup file and copies them into Docker /backups for Operations Center visibility.
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
    --operator)
      OPERATOR_NAME="$2"
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
backup_hash="$(sha256sum "$backup_path" | awk '{print tolower($1)}')"
if [ -f "$sha_path" ]; then
  expected_hash="$(awk '{print tolower($1)}' "$sha_path")"
  if [ "$expected_hash" != "$backup_hash" ]; then
    echo "Backup checksum mismatch: $backup_path" >&2
    exit 1
  fi
  echo "Backup checksum verified: $backup_path"
else
  echo "Backup checksum sidecar not found; continuing without checksum verification."
fi

timestamp="$(date +%Y%m%d_%H%M%S)"
started_at="$(date +%s)"
if [ -z "$OPERATOR_NAME" ]; then
  OPERATOR_NAME="${USER:-operations-script}"
fi
if [ -z "$RESTORE_DATABASE" ]; then
  RESTORE_DATABASE="zhubao_restore_check_$timestamp"
fi
assert_safe_name "$RESTORE_DATABASE" "RestoreDatabase"

remote_sql="/tmp/$RESTORE_DATABASE.sql"
echo "== Copy backup into MySQL container =="
docker cp "$backup_path" "zhubao-erp-mysql:$remote_sql"

echo "== Restore backup into temporary database: $RESTORE_DATABASE =="
set +e
restore_output="$(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T \
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
' 2>&1)"
restore_status="$?"
set -e
printf '%s\n' "$restore_output"
if [ "$restore_status" -ne 0 ]; then
  exit "$restore_status"
fi

restored_table_count="$(printf '%s\n' "$restore_output" | sed -n 's/^Restored table count:[[:space:]]*//p' | tail -n 1)"
case "$restored_table_count" in
  *[!0-9]*|"")
    restored_table_count="0"
    ;;
esac

echo "Restore verification passed: $RESTORE_DATABASE"
if [ "$KEEP_DATABASE" = "true" ]; then
  echo "Temporary restore database was kept for inspection."
else
  echo "Temporary restore database was removed."
fi

completed_at="$(date -Iseconds)"
duration_seconds="$(( $(date +%s) - started_at ))"
marker_name="verify-restore-$timestamp.log"
marker_dir="$(dirname "$backup_path")"
marker_path="$marker_dir/$marker_name"
{
  echo "status=passed"
  echo "checkedAt=$completed_at"
  echo "backupFile=$backup_path"
  echo "backupChecksum=sha256:$backup_hash"
  echo "restoreDatabase=$RESTORE_DATABASE"
  echo "minimumTableCount=$MINIMUM_TABLE_COUNT"
  echo "restoredTableCount=$restored_table_count"
  echo "operator=$OPERATOR_NAME"
  echo "durationSeconds=$duration_seconds"
  echo "keepDatabase=$KEEP_DATABASE"
} > "$marker_path"
sha256sum "$marker_path" > "$marker_path.sha256"

json_escape() {
  printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'
}

record_name="verify-restore-$timestamp.json"
record_path="$marker_dir/$record_name"
if [ "$KEEP_DATABASE" = "true" ]; then
  cleanup_detail="Temporary restore database kept for manual inspection."
else
  cleanup_detail="Temporary restore database removed after verification."
fi
cat > "$record_path" <<JSON
{
  "schemaVersion": "ops.restore-drill.v1",
  "status": "passed",
  "checkedAt": "$(json_escape "$completed_at")",
  "backupFile": "$(json_escape "$backup_path")",
  "backupChecksum": "sha256:$backup_hash",
  "restoreDatabase": "$(json_escape "$RESTORE_DATABASE")",
  "minimumTableCount": $MINIMUM_TABLE_COUNT,
  "restoredTableCount": $restored_table_count,
  "operator": "$(json_escape "$OPERATOR_NAME")",
  "durationSeconds": $duration_seconds,
  "keepDatabase": $KEEP_DATABASE,
  "detail": "Non-destructive restore verification passed.",
  "checks": [
    {
      "name": "backup_checksum",
      "status": "passed",
      "detail": "Backup SHA-256 recorded; sidecar verified when present."
    },
    {
      "name": "table_count",
      "status": "passed",
      "detail": "Restored $restored_table_count tables; minimum required $MINIMUM_TABLE_COUNT."
    },
    {
      "name": "temporary_database_cleanup",
      "status": "passed",
      "detail": "$(json_escape "$cleanup_detail")"
    }
  ]
}
JSON
sha256sum "$record_path" > "$record_path.sha256"

echo "== Publish restore drill marker =="
docker cp "$marker_path" "zhubao-erp-mysql:/backups/$marker_name"
docker cp "$marker_path.sha256" "zhubao-erp-mysql:/backups/$marker_name.sha256"
docker cp "$record_path" "zhubao-erp-mysql:/backups/$record_name"
docker cp "$record_path.sha256" "zhubao-erp-mysql:/backups/$record_name.sha256"
echo "Restore drill marker written: $marker_path"
echo "Restore drill structured record written: $record_path"
echo "Operations Center marker path: /backups/$marker_name"
echo "Operations Center structured record path: /backups/$record_name"
