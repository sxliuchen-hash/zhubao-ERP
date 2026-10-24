#!/bin/bash
# 在测试服务器上用已上传的编译产物切换镜像。不执行数据库迁移。
set -euo pipefail

TAG="${1:?缺少镜像标签}"
PART="${2:-both}"
REL="/opt/zhubao-erp/releases/${TAG}"
ARCHIVE="/home/ubuntu/${TAG}.tar.gz"
RUNTIME="/opt/zhubao-erp/runtime"

sudo mkdir -p "${REL}/stage" "${REL}/rollback"
sudo cp -a "${RUNTIME}/.env" "${REL}/rollback/.env"
if [[ -f "${RUNTIME}/DEPLOYMENT.json" ]]; then
  sudo cp -a "${RUNTIME}/DEPLOYMENT.json" "${REL}/rollback/DEPLOYMENT.json"
fi
sudo tar -xzf "${ARCHIVE}" -C "${REL}/stage"
cd "${REL}/stage"

if [[ "${PART}" != "frontend" ]]; then
  sudo docker build -f docker/backend.Dockerfile -t "zhubao-erp-backend:${TAG}" .
fi
if [[ "${PART}" != "backend" ]]; then
  sudo docker build -f docker/frontend.Dockerfile -t "zhubao-erp-frontend:${TAG}" .
fi

sudo TAG="${TAG}" python3 - << 'PY'
import os
from pathlib import Path
tag = os.environ["TAG"]
path = Path("/opt/zhubao-erp/runtime/.env")
lines = path.read_text(encoding="utf-8").splitlines()
found = False
updated = []
for line in lines:
    if line.startswith("ZHUBAO_IMAGE_TAG="):
        updated.append(f"ZHUBAO_IMAGE_TAG={tag}")
        found = True
    else:
        updated.append(line)
if not found:
    updated.append(f"ZHUBAO_IMAGE_TAG={tag}")
path.write_text("\n".join(updated) + "\n", encoding="utf-8")
PY

services=()
if [[ "${PART}" != "frontend" ]]; then
  services+=(backend)
fi
if [[ "${PART}" != "backend" ]]; then
  services+=(frontend)
fi
cd "${RUNTIME}"
sudo docker compose up -d --no-deps --force-recreate "${services[@]}"

healthy=0
for _ in $(seq 1 45); do
  if curl -kfsS "https://127.0.0.1/actuator/health" | grep -q '"status":"UP"'; then
    healthy=1
    break
  fi
  sleep 3
done
if [[ "${healthy}" != "1" ]]; then
  echo "健康检查未通过，正在恢复上一版配置"
  sudo cp -a "${REL}/rollback/.env" "${RUNTIME}/.env"
  sudo docker compose up -d --no-deps --force-recreate backend frontend
  exit 1
fi

sudo TAG="${TAG}" REL="${REL}" python3 - << 'PY'
import json, os
from datetime import datetime, timezone
from pathlib import Path
tag = os.environ["TAG"]
rel = os.environ["REL"]
path = Path("/opt/zhubao-erp/runtime/DEPLOYMENT.json")
data = json.loads(path.read_text(encoding="utf-8")) if path.exists() else {}
data["previousImageTag"] = data.get("imageTag")
data["imageTag"] = tag
data["frontendImageTag"] = tag
data["deployedAt"] = datetime.now(timezone.utc).isoformat()
data["frontendDeployedAt"] = data["deployedAt"]
data["rollbackDirectory"] = rel + "/rollback"
data["frontendRollbackDirectory"] = rel + "/rollback"
data["note"] = "2026-10-24 来料、生产、质量、财务、用户、标签、收款和单据维护的短字段改为两列；无新迁移，MySQL 未重建"
path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
PY

echo "HOMEPAGE_SHA256 $(curl -kfsS https://127.0.0.1/ | sha256sum | awk '{print $1}')"
echo "HEALTH $(curl -kfsS https://127.0.0.1/actuator/health)"
sudo docker ps --format '{{.Names}} {{.Image}} {{.Status}}'
