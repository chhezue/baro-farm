# JDK 이미지 사용 (jcmd 포함)
FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app

ENV APP_NAME=baro-buyer

COPY baro-buyer/build/libs/baro-buyer-*.jar app.jar
EXPOSE 8082
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8082/actuator/health || exit 1
ENTRYPOINT ["sh", "-c", "java -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Dapp.name=$APP_NAME $JAVA_TOOL_OPTIONS $JAVA_OPTS -jar app.jar"]

