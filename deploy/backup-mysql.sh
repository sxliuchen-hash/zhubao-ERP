#!/usr/bin/env sh
set -eu

ENV_FILE=".env"
COMPOSE_FILE="docker-compose.yml"
BACKUP_NAME=""
EXPORT_DIR="runtime-backups"
UPLOAD_COS="false"
COS_PREFIX=""

show_help() {
  cat <<'HELP'
Create a MySQL backup from the Docker Compose mysql service and optionally upload it to Tencent COS.

Usage:
  ./backup-mysql.sh
  ./backup-mysql.sh --upload-cos --cos-prefix prod/mysql/

Options:
  --env-file PATH       Compose env file. Default: .env
  --compose-file PATH   Compose file. Default: docker-compose.yml
  --name NAME           Optional safe backup base name, without extension.
  --export-dir PATH     Host directory for exported backup files. Default: runtime-backups
  --upload-cos          Upload exported .sql/.sha256 files with coscmd.
  --cos-prefix PREFIX   COS object prefix. Defaults to ZHUBAO_COS_BACKUP_PREFIX from .env.
  -h, --help            Show this help.
HELP
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --env-file)
      ENV_FILE="$2"
      shift 2
      ;;
    --compose-file)
      COMPOSE_FILE="$2"
      shift 2
      ;;
    --name)
      BACKUP_NAME="$2"
      shift 2
      ;;
    --export-dir)
      EXPORT_DIR="$2"
      shift 2
      ;;
    --upload-cos)
      UPLOAD_COS="true"
      shift
      ;;
    --cos-prefix)
      COS_PREFIX="$2"
      shift 2
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

read_env_value() {
  key="$1"
  file="$2"
  [ -f "$file" ] || return 0
  grep -E "^[[:space:]]*$key[[:space:]]*=" "$file" | tail -n 1 | sed -E "s/^[^=]+=//; s/^['\"]//; s/['\"]$//"
}

assert_safe_name() {
  name="$1"
  label="$2"
  case "$name" in
    *[!A-Za-z0-9_.-]*|"")
      echo "$label contains unsafe characters. Allowed: A-Z a-z 0-9 _ . -" >&2
      exit 2
      ;;
  esac
}

if [ ! -f "$ENV_FILE" ]; then
  if [ -f ".env.example" ]; then
    cp ".env.example" "$ENV_FILE"
    echo "Created $ENV_FILE from .env.example. Review passwords before production use."
  else
    echo "Env file not found: $ENV_FILE" >&2
    exit 1
  fi
fi

timestamp="$(date +%Y%m%d-%H%M%S)"
if [ -z "$BACKUP_NAME" ]; then
  BACKUP_NAME="zhubao_erp_mysql_$timestamp"
fi
assert_safe_name "$BACKUP_NAME" "BackupName"

remote_sql="$BACKUP_NAME.sql"
remote_sha="$remote_sql.sha256"

echo "== Create MySQL backup in Docker volume =="
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T -e "BACKUP_NAME=$remote_sql" mysql sh -c '
set -eu
target="/backups/$BACKUP_NAME"
mysqldump --single-transaction --routines --triggers -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" > "$target"
sha256sum "$target" > "$target.sha256"
ls -lh "$target" "$target.sha256"
'

mkdir -p "$EXPORT_DIR"
local_sql="$EXPORT_DIR/$remote_sql"
local_sha="$EXPORT_DIR/$remote_sha"

echo "== Export backup files to host =="
docker cp "zhubao-erp-mysql:/backups/$remote_sql" "$local_sql"
docker cp "zhubao-erp-mysql:/backups/$remote_sha" "$local_sha"

expected_hash="$(awk '{print tolower($1)}' "$local_sha")"
actual_hash="$(sha256sum "$local_sql" | awk '{print tolower($1)}')"
if [ "$expected_hash" != "$actual_hash" ]; then
  echo "Exported backup checksum mismatch: $local_sql" >&2
  exit 1
fi
echo "Backup checksum verified: $local_sql"

if [ "$UPLOAD_COS" = "true" ]; then
  if [ -z "$COS_PREFIX" ]; then
    COS_PREFIX="$(read_env_value ZHUBAO_COS_BACKUP_PREFIX "$ENV_FILE" || true)"
  fi
  if [ -z "$COS_PREFIX" ]; then
    echo "Cos prefix is required when --upload-cos is used and ZHUBAO_COS_BACKUP_PREFIX is empty." >&2
    exit 1
  fi
  COS_PREFIX="${COS_PREFIX#/}"
  case "$COS_PREFIX" in
    */) ;;
    *) COS_PREFIX="$COS_PREFIX/" ;;
  esac
  echo "== Upload backup files to Tencent COS with coscmd =="
  coscmd upload "$local_sql" "$COS_PREFIX$remote_sql"
  coscmd upload "$local_sha" "$COS_PREFIX$remote_sha"
  echo "COS upload completed: $COS_PREFIX$remote_sql"
fi

echo "Backup completed."
echo "Container path: /backups/$remote_sql"
echo "Host export: $local_sql"
