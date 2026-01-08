FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

ENV APP_NAME=baro-eureka

COPY baro-cloud/eureka/build/libs/eureka-*.jar app.jar
EXPOSE 8761
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8761/actuator/health || exit 1
ENV APP_NAME=eureka
ENTRYPOINT ["sh", "-c", "java -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Dapp.name=$APP_NAME $JAVA_TOOL_OPTIONS $JAVA_OPTS -jar app.jar"]

