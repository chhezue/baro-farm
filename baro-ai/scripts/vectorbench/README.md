# 벡터·색인 벤치마크 — 추천·색인 경로의 설계 판단을 숫자로

"`knn`으로 바꿔야 하나", "다양화가 효과가 있나", "묶음 색인이 얼마나 빠른가" 같은 질문에 **재본 뒤** 답하기 위한 도구다. 결론 규칙은 재기 전에 감사 문서에 적고, 여기서는 그 규칙에 넣을 숫자만 만든다. 판정 규칙의 SSOT는 [`docs/audits/vector-similarity/002`](../../docs/audits/vector-similarity/002-vector-transfer-knn-and-diversity.md)(M-1·M-2)와 [`docs/audits/keyword-search/002`](../../docs/audits/keyword-search/002-index-throughput-and-search-log.md)(M-3)다.

## 준비

표준 라이브러리만 쓴다(`requests`·`numpy` 없음). 스크립트별로 필요한 것이 다르다.

| 스크립트 | 무엇을 재나 | 필요한 것 |
|----------|-------------|-----------|
| `knn_bench.py` | `script_score` 선형 스캔 vs 최상위 `knn`의 지연·일치율을 상품 수별로 | 로컬 Elasticsearch |
| `diversity.py` | Max-Min 다양화 ON/OFF의 카테고리 중복률·유사도 손실 | Gemini 키 (첫 실행만 — 임베딩은 `embeddings-cache.json`에 캐시) |
| `bulk_vs_single.py` | ES 문서 1건씩 저장 vs `_bulk` | 로컬 Elasticsearch |
| `embed_batch.py` | Gemini 임베딩 1건씩 vs 묶음 호출 | Gemini 키 |

로컬 ES는 스톡 이미지면 된다 — 벡터 벤치마크는 nori 분석기와 무관하다.

```bash
docker run -d --name baro-es-bench -p 9200:9200 -e discovery.type=single-node -e xpack.security.enabled=false -e "ES_JAVA_OPTS=-Xms1g -Xmx1g" elasticsearch:8.12.0
```

Gemini 키는 셸 프로필에 `BARO_AI_GEMINI_KEY`로 있다(값을 출력하지 않는다):

```bash
eval "$(grep -m1 '^export BARO_AI_GEMINI_KEY' ~/.zshrc)"
```

## 실행

```bash
cd baro-ai/scripts/vectorbench
python3 knn_bench.py --docs 1000 10000          # 10만 건은 시드 약 9분
python3 diversity.py --selfcheck                 # Java 구현과 같은 답인지 먼저 확인
python3 diversity.py --k 5 10 --multiplier 2 3 5 --pace 7
python3 bulk_vs_single.py --docs 1000 --bulk-size 100
python3 embed_batch.py --n 50 --batch 10 50
```

결과는 화면에 찍히고, 원본 JSON은 각 감사 폴더의 `vectorbench/`에 시각과 함께 저장된다. 해석은 감사 문서·refactor-log가 한다 — 여기서는 숫자를 만들 뿐이다.

## 알아 둘 것

- **Gemini 무료 티어 한도가 빠듯하다.** 2026-09-06 실측: 1건씩 13번째 호출부터 429, 10건 묶음은 통과, 25건 이상 묶음은 분당 한도 초과. `diversity.py`가 10개씩 `--pace`초 간격으로 보내는 이유다.
- `knn_bench.py`의 벡터는 난수다. 지연은 그대로 믿어도 되지만 일치율은 참고값이다 — 난수는 서로 거의 직교해 HNSW에 가장 불리한 데이터다.
- `diversity.py`의 다양화 코드는 `MaxMinDiversityService.selectDiverse`를 옮긴 것이다. `--selfcheck`가 `MaxMinDiversityServiceTest`와 같은 예제(a, c, d)로 두 구현이 같은 답을 내는지 확인한다. Java 쪽을 고치면 여기도 같이 고치고 예제를 다시 맞춘다.
- `embeddings-cache.json`은 커밋하지 않는다(약 3MB, 재생성 가능).
