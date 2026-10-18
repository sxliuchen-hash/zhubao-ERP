FROM eclipse-temurin:17-jre-alpine

ARG JAR_FILE=backend/target/zhubao-erp-backend-0.0.1-SNAPSHOT.jar

ENV TZ=Asia/Shanghai \
    SERVER_PORT=8080 \
    SPRING_PROFILES_ACTIVE=mysql \
    ZHUBAO_DOCUMENTS_STORAGE_LOCAL_ROOT=/app/evidence \
    ZHUBAO_BACKUP_ROOT=/backups \
    ZHUBAO_LOG_FILE=/logs/zhubao-erp.log \
    LOGGING_FILE_NAME=/logs/zhubao-erp.log

WORKDIR /app

RUN apk add --no-cache ffmpeg \
    && mkdir -p /app/evidence /logs /backups \
    && addgroup -S zhubao \
    && adduser -S zhubao -G zhubao \
    && chown -R zhubao:zhubao /app /logs /backups

COPY ${JAR_FILE} /app/app.jar

USER zhubao

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
