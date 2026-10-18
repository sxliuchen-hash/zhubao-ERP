#!/usr/bin/env sh
set -eu

ENV_FILE=".env"
COMPOSE_FILE="docker-compose.yml"
EXPORT_DIR="runtime-backups"
BATCH=""

while [ "$#" -gt 0 ]; do
  case "$1" in
    --env-file) ENV_FILE="$2"; shift 2 ;;
    --compose-file) COMPOSE_FILE="$2"; shift 2 ;;
    --export-dir) EXPORT_DIR="$2"; shift 2 ;;
    --name) BATCH="$2"; shift 2 ;;
    -h|--help)
      echo '用法：./backup-bundle.sh [--name 批次名] [--export-dir 目录] [--env-file 文件] [--compose-file 文件]'
      echo '执行前必须停止 backend，脚本只读取 MySQL 和附件卷。'
      exit 0 ;;
    *) echo "未知参数：$1" >&2; exit 2 ;;
  esac
done

cd "$(dirname "$0")"
[ -n "$BATCH" ] || BATCH="zhubao_bundle_$(date +%Y%m%d_%H%M%S)"
case "$BATCH" in *[!A-Za-z0-9_.-]*|"") echo '批次名含非法字符' >&2; exit 2 ;; esac

backend_running="$(docker inspect -f '{{.State.Running}}' zhubao-erp-backend)"
if [ "$backend_running" != "false" ]; then
  echo '请先停止 backend，确保数据库与附件卷在同一停写窗口内备份。' >&2
  exit 1
fi
backend_image="$(docker inspect -f '{{.Config.Image}}' zhubao-erp-backend)"
mkdir -p "$EXPORT_DIR"
export_root="$(cd "$EXPORT_DIR" && pwd)"
for suffix in sql sql.sha256 evidence.tar.gz evidence.tar.gz.sha256 \
  evidence.files.sha256 evidence.files.sha256.sha256 manifest manifest.sha256; do
  if [ -e "$export_root/$BATCH.$suffix" ]; then
    echo "批次已存在：$BATCH" >&2
    exit 1
  fi
done

sh ./backup-mysql.sh --env-file "$ENV_FILE" --compose-file "$COMPOSE_FILE" \
  --name "$BATCH" --export-dir "$export_root"

docker run --rm --network none --user 0:0 --volumes-from zhubao-erp-backend \
  -v "$export_root:/export" \
  -e "BATCH=$BATCH" -e "HOST_UID=$(id -u)" -e "HOST_GID=$(id -g)" \
  --entrypoint sh "$backend_image" -c '
set -eu
umask 077
cd /app/evidence
if [ -n "$(find . -type l -print -quit)" ]; then
  echo "附件卷含符号链接，拒绝生成不安全归档" >&2
  exit 1
fi
find . -type f -exec sha256sum {} \; | sort > "/export/$BATCH.evidence.files.sha256"
tar -czf "/export/$BATCH.evidence.tar.gz" .
find . -type f -exec sha256sum {} \; | sort | cmp - "/export/$BATCH.evidence.files.sha256"
sha256sum "/export/$BATCH.evidence.tar.gz" > "/export/$BATCH.evidence.tar.gz.sha256"
sha256sum "/export/$BATCH.evidence.files.sha256" > "/export/$BATCH.evidence.files.sha256.sha256"
chown "$HOST_UID:$HOST_GID" "/export/$BATCH.evidence.tar.gz" "/export/$BATCH.evidence.tar.gz.sha256" "/export/$BATCH.evidence.files.sha256" "/export/$BATCH.evidence.files.sha256.sha256"
'

check_hash() {
  expected="$(awk 'NR == 1 { print tolower($1) }' "$1.sha256")"
  actual="$(sha256sum "$1" | awk '{ print tolower($1) }')"
  [ "$expected" = "$actual" ] || { echo "校验和不一致：$1" >&2; exit 1; }
}
check_hash "$export_root/$BATCH.sql"
check_hash "$export_root/$BATCH.evidence.tar.gz"
check_hash "$export_root/$BATCH.evidence.files.sha256"

file_count="$(wc -l < "$export_root/$BATCH.evidence.files.sha256" | tr -d ' ')"
derived_count="$(awk '$2 ~ /^\.\/derived-images\// || $2 ~ /^\.\/derived-videos\// { count++ } END { print count+0 }' "$export_root/$BATCH.evidence.files.sha256")"
original_count="$((file_count - derived_count))"
metadata_count="$(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T mysql sh -c \
  'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" -N "$MYSQL_DATABASE" -e "select count(*) from doc_evidence_attachment"')"
table_count="$(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T mysql sh -c \
  'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" -N "$MYSQL_DATABASE" -e "select count(*) from information_schema.tables where table_schema = database()"')"
manifest="$export_root/$BATCH.manifest"
{
  echo 'format=zhubao-attachment-bundle-v1'
  echo "batchId=$BATCH"
  echo "createdAt=$(date -Iseconds)"
  echo "sqlSha256=$(sha256sum "$export_root/$BATCH.sql" | awk '{ print $1 }')"
  echo "evidenceSha256=$(sha256sum "$export_root/$BATCH.evidence.tar.gz" | awk '{ print $1 }')"
  echo "fileListSha256=$(sha256sum "$export_root/$BATCH.evidence.files.sha256" | awk '{ print $1 }')"
  echo "tableCount=$table_count"
  echo "metadataCount=$metadata_count"
  echo "fileCount=$file_count"
  echo "originalCount=$original_count"
  echo "derivedCount=$derived_count"
  echo 'restoreStatus=not-verified'
} > "$manifest"
sha256sum "$manifest" > "$manifest.sha256"
echo "成套备份已导出：$manifest；隔离恢复前不得标记为可用。"
