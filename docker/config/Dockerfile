# Multi-stage build: JDK에서 jcmd 및 도구 복사
FROM eclipse-temurin:21-jdk-alpine AS jdk-tools

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

ENV APP_NAME=baro-config

# JDK에서 jcmd 복사
COPY --from=jdk-tools /opt/java/openjdk/bin/jcmd /usr/local/bin/jcmd

COPY baro-cloud/config/build/libs/config-*.jar app.jar
EXPOSE 8888
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8888/actuator/health || exit 1
ENV APP_NAME=config
ENTRYPOINT ["sh", "-c", "java -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Dapp.name=$APP_NAME $JAVA_TOOL_OPTIONS $JAVA_OPTS -jar app.jar"]

