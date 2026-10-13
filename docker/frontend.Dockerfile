FROM nginx:1.27-alpine

RUN apk add --no-cache openssl

COPY docker/nginx/default.conf /etc/nginx/conf.d/default.conf
COPY docker/nginx/40-generate-tls.sh /docker-entrypoint.d/40-generate-tls.sh
COPY frontend/dist/ /usr/share/nginx/html/

RUN chmod +x /docker-entrypoint.d/40-generate-tls.sh

EXPOSE 80 443
