# localbench — 앱을 실제로 띄운 상태에서 재는 것들

`vectorbench/`가 앱 없이 Elasticsearch·Gemini만 두드려 재는 도구라면, 여기는 **앱·ES·Kafka가 다 떠 있어야** 의미가 있는 것을 잰다. 스레드 풀 포화나 비동기 작업 유실처럼 단위 테스트로는 잡히지 않고 코드를 읽어서도 알 수 없는 값이 대상이다.

## 준비

```bash
docker compose -f baro-ai/docker/local/docker-compose.yml up -d --build
```

ES(9201, nori 포함)와 Kafka(9093)가 뜬다. 그다음 앱을 로컬 인프라에 붙여 띄운다 — 로그 파일 경로를 기억해 둔다(아래 스크립트가 그 파일을 읽는다).

```bash
eval "$(grep -m1 '^export BARO_AI_GEMINI_KEY' ~/.zshrc)"   # 값은 출력하지 않는다
ELASTICSEARCH_URIS=http://localhost:9201 \
KAFKA_BOOTSTRAP_SERVERS=localhost:9093 \
SHOPPING_SERVICE_URL=http://localhost:8099 \
./gradlew :baro-ai:bootRun --console=plain > /tmp/baro-ai-app.log 2>&1 &
```

`SHOPPING_SERVICE_URL`은 장바구니 스텁이다. `baro-shopping`은 MySQL까지 필요해서 레시피 경로를 볼 때만 스텁으로 대신한다.

## 실행

| 파일 | 무엇을 재나 | 결과가 쓰이는 곳 |
|---|---|---|
| `async_queue.py` | 회원 검색을 몰아넣고 비동기 부가 작업(검색 로그 적재 + 프로필 갱신)의 **유실률**과 검색 응답 지연 | [`audits/taste-vector/003`](../../docs/audits/taste-vector/003-shared-executor-saturation.md) A-1 |

```bash
python3 scripts/localbench/async_queue.py \
  --app-log /tmp/baro-ai-app.log \
  --out docs/audits/taste-vector/localbench/async-queue-$(date +%Y%m%d-%H%M%S).json
```

폐기 수는 앱 로그의 경고 줄에서, 적재 수는 `search_logs` 인덱스 문서 수 증가분에서 각각 따로 센다. **둘의 합이 보낸 요청 수와 같아야** 측정이 믿을 만하다 — 스크립트가 `accounted` 필드로 같이 출력하므로 결과를 인용하기 전에 이 값을 확인한다.

## 알아 둘 것

**컨테이너 안에서 Kafka CLI를 쓸 때는 내부 리스너 주소를 쓴다** — `--bootstrap-server kafka:29092`. `localhost:9092`로 붙으면 브로커가 호스트용 주소(`localhost:9093`)를 알려 주는데 컨테이너 안에는 그 포트가 없어 명령이 멈춘 채로 끝나지 않는다.

**포트는 팀 공용 `docker-compose.data.yml`과 일부러 어긋나게 뒀다** — 9200·9092가 다른 용도로 이미 떠 있어도 이 환경을 나란히 띄울 수 있게 하기 위해서다. 앱에 넘기는 주소(9201·9093)를 바꿀 때는 compose의 포트 매핑도 같이 본다.

**측정이 끝나면 정리한다.** 앱은 gradle 프로세스를 종료하고, 인프라는 아래 명령으로 내린다. `-v`(볼륨 삭제)는 붙이지 않는다 — 지우려면 사람이 직접 판단한다.

```bash
docker compose -f baro-ai/docker/local/docker-compose.yml down
```
