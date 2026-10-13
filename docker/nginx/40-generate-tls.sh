#!/bin/sh
# 没有现场证书时生成自签证书，让手机用 HTTPS 打开摄像头。
set -eu

CERT_DIR=/etc/nginx/certs
mkdir -p "$CERT_DIR"

WANT=$(printf '%s' "${ZHUBAO_TLS_SAN:-}" | tr -d ' ')
STAMP="$CERT_DIR/san.stamp"

if [ -s "$CERT_DIR/server.crt" ] && [ -s "$CERT_DIR/server.key" ]; then
  if [ ! -f "$STAMP" ]; then
    exit 0
  fi
  CURRENT=$(cat "$STAMP" 2>/dev/null || true)
  if [ "$CURRENT" = "$WANT" ]; then
    exit 0
  fi
fi

SAN="DNS:localhost,IP:127.0.0.1"
if [ -n "$WANT" ]; then
  OLD_IFS=$IFS
  IFS=','
  for name in $WANT; do
    if [ -z "$name" ]; then
      continue
    fi
    case "$name" in
      *:*)
        SAN="$SAN,IP:$name"
        ;;
      *)
        if printf '%s' "$name" | grep -Eq '^[0-9]{1,3}(\.[0-9]{1,3}){3}$'; then
          SAN="$SAN,IP:$name"
        else
          SAN="$SAN,DNS:$name"
        fi
        ;;
    esac
  done
  IFS=$OLD_IFS
fi

openssl req -x509 -nodes -newkey rsa:2048 -days 825 \
  -keyout "$CERT_DIR/server.key" \
  -out "$CERT_DIR/server.crt" \
  -subj "/CN=zhubao-erp" \
  -addext "subjectAltName=$SAN"
chmod 644 "$CERT_DIR/server.crt"
chmod 640 "$CERT_DIR/server.key"
printf '%s' "$WANT" > "$STAMP"
