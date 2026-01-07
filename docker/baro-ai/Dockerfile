FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

ENV APP_NAME=baro-ai

# JAR 복사
COPY baro-ai/build/libs/baro-ai-*.jar app.jar

EXPOSE 8092

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8092/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Dapp.name=$APP_NAME $JAVA_TOOL_OPTIONS $JAVA_OPTS -jar app.jar"]


