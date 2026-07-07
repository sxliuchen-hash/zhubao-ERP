#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")"

if [ ! -f ".env" ]; then
  cp ".env.example" ".env"
  echo "Created .env from .env.example. Review passwords before production use."
fi

image_tar="$(find images -maxdepth 1 -name '*.tar' -type f 2>/dev/null | sort | tail -n 1 || true)"
if [ -n "$image_tar" ]; then
  echo "Loading Docker images from $image_tar..."
  docker load -i "$image_tar"
fi

docker compose --env-file .env -f docker-compose.yml up -d
docker compose --env-file .env -f docker-compose.yml ps
