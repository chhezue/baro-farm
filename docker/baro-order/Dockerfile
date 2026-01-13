# JDK 이미지 사용 (jcmd 포함)
FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app

ENV APP_NAME=baro-order

COPY baro-order/build/libs/baro-order-*.jar app.jar
EXPOSE 8087
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8087/actuator/health || exit 1
ENTRYPOINT ["sh", "-c", "java -Dapp.name=$APP_NAME $JAVA_TOOL_OPTIONS $JAVA_OPTS -jar app.jar"]

