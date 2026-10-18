#!/usr/bin/env sh
set -eu

MANIFEST=""
ENV_FILE=".env"
COMPOSE_FILE="docker-compose.yml"
while [ "$#" -gt 0 ]; do
  case "$1" in
    --manifest) MANIFEST="$2"; shift 2 ;;
    --env-file) ENV_FILE="$2"; shift 2 ;;
    --compose-file) COMPOSE_FILE="$2"; shift 2 ;;
    -h|--help)
      echo '用法：./verify-bundle.sh --manifest runtime-backups/<批次>.manifest'
      echo '在隔离目录和临时数据库中恢复，不修改当前业务库或附件卷。'
      exit 0 ;;
    *) echo "未知参数：$1" >&2; exit 2 ;;
  esac
done

cd "$(dirname "$0")"
[ -f "$MANIFEST" ] || { echo '成套清单不存在' >&2; exit 1; }
manifest_name="$(basename "$MANIFEST")"
root="$(cd "$(dirname "$MANIFEST")" && pwd)"
MANIFEST="$root/$manifest_name"
field() { sed -n "s/^$1=//p" "$MANIFEST" | tail -n 1; }
batch="$(field batchId)"
case "$batch" in *[!A-Za-z0-9_.-]*|"") echo '批次名非法' >&2; exit 2 ;; esac
[ "$(field format)" = 'zhubao-attachment-bundle-v1' ] || { echo '清单格式不支持' >&2; exit 1; }
sql="$root/$batch.sql"
archive="$root/$batch.evidence.tar.gz"
file_list="$root/$batch.evidence.files.sha256"
for file in "$sql" "$archive" "$file_list" "$MANIFEST" "$MANIFEST.sha256"; do
  [ -f "$file" ] || { echo "成套文件缺失：$file" >&2; exit 1; }
done
manifest_hash="$(awk 'NR == 1 { print tolower($1) }' "$MANIFEST.sha256")"
actual="$(sha256sum "$MANIFEST" | awk '{ print tolower($1) }')"
[ "$manifest_hash" = "$actual" ] || { echo '清单校验和不符' >&2; exit 1; }
check_hash() {
  expected="$(field "$1")"
  actual="$(sha256sum "$2" | awk '{ print tolower($1) }')"
  [ "$expected" = "$actual" ] || { echo "校验和不符：$2" >&2; exit 1; }
}
check_hash sqlSha256 "$sql"
check_hash evidenceSha256 "$archive"
check_hash fileListSha256 "$file_list"

tmp="$(mktemp -d)"
restore_db="zhubao_bundle_restore_$(date +%s)_$$"
remote_sql="/tmp/$restore_db.sql"
db_created=0
cleanup() {
  cleanup_status=0
  if [ "$db_created" -eq 1 ]; then
    docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T \
      -e "RESTORE_DB=$restore_db" mysql sh -c \
      'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "DROP DATABASE \`$RESTORE_DB\`"' >/dev/null 2>&1 || \
      { echo "临时数据库清理失败：$restore_db" >&2; cleanup_status=1; }
    [ "$cleanup_status" -ne 0 ] || db_created=0
  fi
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T \
    -e "RESTORE_SQL=$remote_sql" mysql sh -c 'rm -f "$RESTORE_SQL"' >/dev/null 2>&1 || true
  rm -rf "$tmp"
  return "$cleanup_status"
}
trap cleanup 0

tar -tzf "$archive" > "$tmp/entries"
if tar -tvzf "$archive" | awk 'substr($0,1,1) == "l" || substr($0,1,1) == "h" { found=1 } END { exit !found }'; then
  echo '附件归档含链接，拒绝解压' >&2
  exit 1
fi
while IFS= read -r entry; do
  case "$entry" in
    /*|../*|*/../*|*/..)
      echo "附件归档含越界路径：$entry" >&2; exit 1 ;;
  esac
done < "$tmp/entries"
mkdir -p "$tmp/evidence"
tar -xzf "$archive" -C "$tmp/evidence"
(cd "$tmp/evidence" && sha256sum -c "$file_list" > "$tmp/files-verified.log")
actual_files="$(find "$tmp/evidence" -type f | wc -l | tr -d ' ')"
expected_files="$(field fileCount)"
[ -n "$expected_files" ] || expected_files="$(field originalCount)"
[ "$actual_files" = "$expected_files" ] || { echo '附件文件数不符' >&2; exit 1; }
actual_derived="$(find "$tmp/evidence/derived-images" "$tmp/evidence/derived-videos" -type f 2>/dev/null | wc -l | tr -d ' ')"
actual_original="$((actual_files - actual_derived))"
if [ -n "$(field fileCount)" ]; then
  [ "$actual_original" = "$(field originalCount)" ] || { echo '附件原件数不符' >&2; exit 1; }
  [ "$actual_derived" = "$(field derivedCount)" ] || { echo '附件派生对象数不符' >&2; exit 1; }
fi

docker cp "$sql" "zhubao-erp-mysql:$remote_sql"
existing_db="$(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T \
  -e "RESTORE_DB=$restore_db" mysql sh -c \
  'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e "select schema_name from information_schema.schemata where schema_name = '\''$RESTORE_DB'\''"')"
[ -z "$existing_db" ] || { echo "临时库名已存在，拒绝覆盖：$restore_db" >&2; exit 1; }
db_created=1
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T \
  -e "RESTORE_DB=$restore_db" mysql sh -c \
  'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "CREATE DATABASE \`$RESTORE_DB\` CHARACTER SET utf8mb4"'
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T \
  -e "RESTORE_DB=$restore_db" -e "RESTORE_SQL=$remote_sql" mysql sh -c \
  'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$RESTORE_DB" < "$RESTORE_SQL"'
table_count="$(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T \
  -e "RESTORE_DB=$restore_db" mysql sh -c \
  'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N "$RESTORE_DB" -e "select count(*) from information_schema.tables where table_schema = database()"')"
metadata_count="$(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T \
  -e "RESTORE_DB=$restore_db" mysql sh -c \
  'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N "$RESTORE_DB" -e "select count(*) from doc_evidence_attachment"')"
[ "$table_count" = "$(field tableCount)" ] || { echo '恢复表数与清单不符' >&2; exit 1; }
[ "$metadata_count" = "$(field metadataCount)" ] || { echo '恢复附件元数据数与清单不符' >&2; exit 1; }
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T \
  -e "RESTORE_DB=$restore_db" mysql sh -c \
  'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -B "$RESTORE_DB" -e "select code, storage_location, checksum from doc_evidence_attachment where storage_location like '\''local://%'\'' and status <> '\''已下线'\''"' \
  > "$tmp/references.tsv"
reference_count=0
while IFS="$(printf '\t')" read -r code location expected; do
  [ -n "$code" ] || continue
  key="${location#local://}"
  case "$key" in
    evidence/*) ;;
    *) echo "附件路径不在 evidence 下：$code" >&2; exit 1 ;;
  esac
  case "$key" in *../*|*/..|/*) echo "附件路径越界：$code" >&2; exit 1 ;; esac
  file="$tmp/evidence/$key"
  [ -f "$file" ] || { echo "恢复附件缺文件：$code" >&2; exit 1; }
  actual="sha256:$(sha256sum "$file" | awk '{ print tolower($1) }')"
  [ "$actual" = "$expected" ] || { echo "恢复附件校验和不符：$code" >&2; exit 1; }
  reference_count=$((reference_count + 1))
done < "$tmp/references.tsv"

cleanup || { echo '隔离恢复结果无法确认清理完成' >&2; exit 1; }
trap - 0
result="$root/$batch.restore-$(date +%Y%m%d_%H%M%S)-$$.txt"
{
  echo 'status=passed'
  echo "batchId=$batch"
  echo "checkedAt=$(date -Iseconds)"
  echo "restoredTableCount=$table_count"
  echo "restoredMetadataCount=$metadata_count"
  echo "verifiedOriginalCount=$actual_original"
  echo "verifiedDerivedCount=$actual_derived"
  echo "verifiedActiveReferences=$reference_count"
  echo "temporaryDatabase=$restore_db"
  echo 'temporaryDatabaseCleanup=on-exit'
} > "$result"
sha256sum "$result" > "$result.sha256"
echo "成套隔离恢复核验通过：$result"
