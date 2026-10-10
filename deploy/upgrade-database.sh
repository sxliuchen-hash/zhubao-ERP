#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"

command="${1:-plan}"
backup_confirmed=0
self_test=0
for arg in "$@"; do
  if [ "$arg" = "--backup-confirmed" ]; then
    backup_confirmed=1
  fi
  if [ "$arg" = "--self-test" ]; then
    self_test=1
  fi
done

if [ "$self_test" -eq 1 ]; then
  if "$0" upgrade >/tmp/zhubao-upgrade-self-test.txt 2>&1; then
    echo "未确认备份时没有拒绝升级" >&2
    exit 1
  fi
  echo "升级脚本自检通过：未确认备份时拒绝执行。自检未启动容器，也未连接数据库。"
  exit 0
fi

if [ "$command" != "plan" ] && [ "$backup_confirmed" -ne 1 ]; then
  echo "升级前必须确认已经完成数据库备份。请先执行 backup-mysql.sh，再加 --backup-confirmed。" >&2
  exit 1
fi

confirmed=""
if [ "$backup_confirmed" -eq 1 ]; then
  confirmed="--backup-confirmed"
fi

docker compose --env-file .env -f docker-compose.yml run --rm --no-deps --entrypoint sh backend -c \
  "java -Dloader.main=com.zhubao.erp.ops.app.DatabaseUpgradeMain -cp /app/app.jar org.springframework.boot.loader.launch.PropertiesLauncher $command --url \"\$ZHUBAO_DB_URL\" --username \"\$ZHUBAO_DB_USERNAME\" --password \"\$ZHUBAO_DB_PASSWORD\" $confirmed"
