#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")"

if [ -f "MANIFEST.sha256" ]; then
  if command -v sha256sum >/dev/null 2>&1; then
    echo "Verifying runtime package manifest..."
    while IFS= read -r line; do
      [ -n "$line" ] || continue
      expected_hash="${line%%  *}"
      relative_path="${line#*  }"
      if [ ! -f "$relative_path" ]; then
        echo "Manifest file missing: $relative_path" >&2
        exit 1
      fi
      actual_hash="$(sha256sum "$relative_path" | awk '{print $1}')"
      if [ "$actual_hash" != "$expected_hash" ]; then
        echo "Manifest hash mismatch: $relative_path" >&2
        exit 1
      fi
    done < "MANIFEST.sha256"
    echo "Manifest verification passed."
  else
    echo "sha256sum not found; skipping MANIFEST.sha256 verification."
  fi
fi

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
