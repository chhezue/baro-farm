FROM openpolicyagent/opa:0.63.0
WORKDIR /app

# opa-bundle JAR 복사 (opa 이미지에 포함)
COPY baro-cloud/opa-bundle/build/libs/opa-bundle-*.jar /app/opa-bundle.jar
COPY baro-cloud/opa/policy /app/policy

# opa 설정 파일 복사
COPY baro-cloud/opa/opa-config.yaml /app/opa-config.yaml

# Java 런타임 설치 (opa-bundle 실행용)
RUN apk add --no-cache openjdk21-jre

EXPOSE 8181 8095

# opa-bundle과 opa를 함께 실행하는 스크립트
RUN echo '#!/bin/sh' > /app/start.sh && \
    echo 'java -jar /app/opa-bundle.jar &' >> /app/start.sh && \
    echo 'sleep 5' >> /app/start.sh && \
    echo 'exec /opa run --server --config-file=/app/opa-config.yaml' >> /app/start.sh && \
    chmod +x /app/start.sh

ENTRYPOINT ["/app/start.sh"]
