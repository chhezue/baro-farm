# Multi-stage build: JDK에서 jcmd 및 도구 복사
FROM eclipse-temurin:21-jdk-alpine AS jdk-tools

FROM eclipse-temurin:21-jre-alpine AS runtime

# 작업 디렉토리 설정
WORKDIR /app

ENV APP_NAME=baro-auth

# JDK에서 jcmd 및 필요한 라이브러리 복사
COPY --from=jdk-tools /opt/java/openjdk/bin/jcmd /usr/local/bin/jcmd
# jcmd 실행에 필요한 libjli.so 복사
RUN mkdir -p /opt/java/openjdk/lib
COPY --from=jdk-tools /opt/java/openjdk/lib/libjli.so /opt/java/openjdk/lib/libjli.so

# JAR 파일 복사
COPY baro-auth/build/libs/baro-auth-*.jar app.jar

# 애플리케이션 포트
EXPOSE 8081

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8081/actuator/health || exit 1

# 실행
ENTRYPOINT ["sh", "-c", "java -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom -Dapp.name=$APP_NAME $JAVA_TOOL_OPTIONS $JAVA_OPTS -jar app.jar"]

