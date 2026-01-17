# JDK 이미지 사용 (jcmd 포함)
FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app

ENV APP_NAME=baro-auth

# JAR 파일 복사
COPY baro-auth/build/libs/baro-auth-*.jar app.jar

# 애플리케이션 포트
EXPOSE 8081

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8081/actuator/health || exit 1

# 실행
ENTRYPOINT ["sh", "-c", "java -Djava.security.egd=file:/dev/./urandom -Dapp.name=$APP_NAME $JAVA_TOOL_OPTIONS $JAVA_OPTS -jar app.jar"]

