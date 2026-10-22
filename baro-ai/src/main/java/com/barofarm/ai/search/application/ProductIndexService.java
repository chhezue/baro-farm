package com.barofarm.ai.search.application;

import com.barofarm.ai.embedding.application.ProductEmbeddingService;
import com.barofarm.ai.search.application.dto.product.ProductIndexRequest;
import com.barofarm.ai.search.domain.ProductDocument;
import com.barofarm.ai.search.infrastructure.elasticsearch.ProductSearchRepository;
import com.barofarm.ai.search.infrastructure.llm.ProductIngredientNormalizer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 상품 색인 서비스.
 *
 * Elasticsearch 상품 인덱스(`product`)에 상품 문서를 쓰고 지웁니다. 조회(검색·자동완성)는
 * {@link ProductSearchService}가 담당합니다.
 *
 * <p><b>인덱스가 하나다.</b> 2026-09-08까지는 자동완성 전용 인덱스(`product-autocomplete`)에도
 * 같은 상품명을 함께 썼는데, Elasticsearch에는 트랜잭션이 없어 두 번의 쓰기 중 하나만 성공할 수
 * 있었습니다 — 자동완성에는 뜨는데 검색에는 없는 상품이 남을 수 있었다는 뜻입니다. 자동완성을
 * 상품 문서의 `productName.ac` 서브필드로 옮겨 그 경우를 없앴습니다.
 *
 * <p><b>바뀐 것만 다시 만듭니다.</b> 가격·재고처럼 무거운 값의 입력이 아닌 것만 바뀐 수정 이벤트에서는
 * 이미 색인된 값을 그대로 옮겨 씁니다. 그전에는 가격이 100원 바뀔 때마다 임베딩 1회와 LLM 1회를
 * 다시 불렀고, 그게 색인 처리량과 Gemini 한도를 동시에 갉아먹고 있었습니다.
 *
 * <p>두 값의 재사용 조건이 다릅니다 — <b>입력이 다르기 때문입니다.</b> 임베딩은 상품명만 보고 만들어지므로
 * 이름이 같으면 재사용합니다. 재료명은 상품명과 카테고리를 함께 보고 뽑으므로 <b>카테고리까지 같아야</b>
 * 재사용합니다. 둘을 한 조건으로 묶으면 카테고리 오분류를 정정한 이벤트에서 옛 재료명이 그대로 남습니다.
 *
 * <p>상품은 묶음으로 색인합니다(2026-09-06, 감사 keyword-search/002 A-1). 임베딩은 묶음당 API 1회,
 * ES 저장은 `saveAll` 1회(bulk)입니다. 재료명 LLM 호출만 상품마다 1회로 남아 있습니다 — 프롬프트를
 * 여러 상품용으로 바꾸면 골든셋 측정(cart-recipe/004, 17/17)이 무효가 되어 별도 라운드로 미뤘습니다.
 */
@Slf4j
@Service
public class ProductIndexService {

    /** 메인 상품 검색 Elasticsearch 레포지토리 */
    private final ProductSearchRepository repository;

    /** 상품명 텍스트 기반 768차원 dense_vector 임베딩 생성 서비스 */
    private final ProductEmbeddingService productEmbeddingService;

    /** 상품명 및 카테고리로부터 요리 재료 표준명을 추출하는 LLM 정규화 컴포넌트 */
    private final ProductIngredientNormalizer ingredientNormalizer;

    /** 색인 시 무거운 값(임베딩·재료명)을 재사용했는지 새로 만들었는지 세는 지표 */
    private final MeterRegistry meterRegistry;

    // @RequiredArgsConstructor를 쓰지 않는 이유: 지표 태그가 호출마다 달라져 필드로 미리 만들어 둘 수 없고,
    // 등록기(MeterRegistry)를 그대로 들고 있어야 하기 때문이다.
    public ProductIndexService(
        ProductSearchRepository repository,
        ProductEmbeddingService productEmbeddingService,
        ProductIngredientNormalizer ingredientNormalizer,
        MeterRegistry meterRegistry
    ) {
        this.repository = repository;
        this.productEmbeddingService = productEmbeddingService;
        this.ingredientNormalizer = ingredientNormalizer;
        this.meterRegistry = meterRegistry;
    }

    /**
     * 무거운 값을 다시 만들었는지 남긴다. 재사용 비율이 낮으면 상류가 상품명·카테고리를 자주 바꾸고
     * 있다는 뜻이고, 그건 곧 외부 AI 호출량이다.
     */
    private void countEnrichment(String kind, boolean reused) {
        Counter.builder("product.index.enrichment")
            .tag("kind", kind)
            .tag("result", reused ? "reused" : "recomputed")
            .description("색인 시 임베딩·재료명을 재사용했는지 새로 만들었는지")
            .register(meterRegistry)
            .increment();
    }

    /**
     * 상품 여러 개를 Elasticsearch 상품 인덱스에 묶음으로 색인합니다.
     * 같은 상품 ID가 묶음 안에 두 번 있으면 뒤의 것이 이깁니다(ES가 같은 ID를 덮어씁니다) — 이벤트 순서가 곧 최종 상태입니다.
     *
     * @param requests 색인할 상품 정보 목록 (빈 목록이면 아무것도 하지 않는다)
     * @return 저장된 ProductDocument 목록 (입력 순서)
     */
    public List<ProductDocument> indexProducts(List<ProductIndexRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }

        // [1단계: 이미 색인된 문서를 읽어 무엇이 바뀌었는지 본다]
        // 입력이 그대로면 무거운 값을 다시 만들 필요가 없다 — 무엇이 "입력"인지는 값마다 다르다(아래 두 판정 메서드).
        Map<UUID, ProductDocument> indexed = loadIndexed(requests);

        // [2단계: 상품명이 바뀐(혹은 벡터가 비어 있는) 상품만 골라 임베딩을 묶음으로 만든다]
        List<Integer> toEmbed = new ArrayList<>();
        for (int i = 0; i < requests.size(); i++) {
            if (!canReuseVector(requests.get(i), indexed.get(requests.get(i).productId()))) {
                toEmbed.add(i);
            }
        }

        Map<Integer, float[]> freshVectors = embedChanged(requests, toEmbed);

        // [3단계: 상품 문서 조립 — 재료명은 바뀐 상품만 LLM으로 다시 뽑는다]
        List<ProductDocument> docs = new ArrayList<>(requests.size());
        for (int i = 0; i < requests.size(); i++) {
            ProductIndexRequest request = requests.get(i);
            ProductDocument previous = indexed.get(request.productId());

            float[] vector;
            if (canReuseVector(request, previous)) {
                vector = previous.getVector();
                countEnrichment("embedding", true);
            } else {
                vector = freshVectors.get(i);
                if (vector == null) {
                    log.error("Product embedding failed. productId={} — 벡터 없이 색인합니다", request.productId());
                }
                countEnrichment("embedding", false);
            }

            String ingredientName;
            if (canReuseIngredientName(request, previous)) {
                ingredientName = previous.getIngredientName();
                countEnrichment("ingredient_name", true);
            } else {
                // 레시피 추천 시점마다 LLM을 호출하는 오버헤드를 없애기 위해, 색인 시점에 상품명과 카테고리로부터
                // 표준 재료명을 1회 추출하여 문서에 함께 저장합니다. (추출 실패 시 빈 문자열 반환)
                ingredientName =
                    ingredientNormalizer.normalize(request.productName(), request.productCategoryName());
                countEnrichment("ingredient_name", false);
            }

            // 정렬 2순위인 updatedAt에는 이벤트가 실어 보낸 값을 그대로 씁니다. 색인 시각을 넣으면
            // 임베딩·재료명 호출에 걸린 시간만큼 상품마다 제각각 밀려 순서가 흔들립니다.
            // 다만 이 값은 baro-shopping이 이벤트를 내보낸 시각이지 상품이 바뀐 시각은 아닙니다.
            Instant updatedAt = request.updatedAt() != null ? request.updatedAt() : Instant.now();

            docs.add(new ProductDocument(
                request.productId(),
                request.productName(),
                request.productCategoryId(),
                request.productCategoryCode(),
                request.productCategoryName(),
                request.price(),
                request.status(),
                updatedAt,
                ingredientName,
                vector));
        }

        // [4단계: bulk 저장 — 상품 수와 무관하게 왕복 1회]
        List<ProductDocument> saved = new ArrayList<>(requests.size());
        repository.saveAll(docs).forEach(saved::add);
        return saved;
    }

    /**
     * 이번 묶음의 상품 ID로 이미 색인된 문서를 한 번에 읽습니다.
     * 조회가 실패해도 색인은 계속합니다 — 최악의 경우 전부 다시 만들 뿐 결과는 같기 때문입니다.
     */
    private Map<UUID, ProductDocument> loadIndexed(List<ProductIndexRequest> requests) {
        List<UUID> ids = requests.stream().map(ProductIndexRequest::productId).distinct().toList();
        Map<UUID, ProductDocument> indexed = new HashMap<>();
        try {
            repository.findAllById(ids).forEach(doc -> indexed.put(doc.getProductId(), doc));
        } catch (Exception e) {
            log.warn("색인된 상품을 읽지 못해 이번 묶음은 전부 새로 만듭니다: {}", e.getMessage());
            return Map.of();
        }
        return indexed;
    }

    /**
     * 이미 만들어 둔 임베딩 벡터를 그대로 써도 되는지 판정합니다.
     *
     * 임베딩은 상품명만 보고 만들어지므로 이름이 같으면 결과도 같습니다. 지난번에 만들지 못해 비어 있었다면
     * 이번 이벤트가 다시 시도할 기회이므로 재사용하지 않습니다.
     */
    private boolean canReuseVector(ProductIndexRequest request, ProductDocument previous) {
        return previous != null
            && request.productName() != null
            && request.productName().equals(previous.getProductName())
            && previous.getVector() != null
            && previous.getVector().length > 0;
    }

    /**
     * 이미 뽑아 둔 재료명을 그대로 써도 되는지 판정합니다.
     *
     * <p>임베딩과 달리 **카테고리명까지 같아야** 합니다. 재료명 추출은 상품명과 카테고리를 함께 넘겨 받고
     * 카테고리를 애매한 상품명을 좁히는 힌트로 쓰기 때문입니다({@code ProductIngredientNormalizer}).
     * 이름은 그대로인데 카테고리만 바뀌는 이벤트(오분류 정정 등)에서 이름만 보고 재사용하면, 옛 카테고리로
     * 뽑은 재료명이 그대로 남고 이름이 바뀌기 전까지 영영 다시 계산되지 않습니다.
     */
    private boolean canReuseIngredientName(ProductIndexRequest request, ProductDocument previous) {
        return previous != null
            && request.productName() != null
            && request.productName().equals(previous.getProductName())
            && java.util.Objects.equals(request.productCategoryName(), previous.getProductCategoryName())
            && previous.getIngredientName() != null
            && !previous.getIngredientName().isBlank();
    }

    /**
     * 새로 만들어야 하는 상품만 모아 임베딩을 한 번에 요청하고, 결과를 원래 자리(묶음 안 인덱스)로 되돌립니다.
     */
    private Map<Integer, float[]> embedChanged(List<ProductIndexRequest> requests, List<Integer> positions) {
        if (positions.isEmpty()) {
            return Map.of();
        }

        List<String> names = positions.stream()
            .map(i -> requests.get(i).productName())
            .toList();
        List<float[]> vectors = productEmbeddingService.embedProducts(names);

        Map<Integer, float[]> byPosition = new HashMap<>();
        for (int i = 0; i < positions.size(); i++) {
            byPosition.put(positions.get(i), i < vectors.size() ? vectors.get(i) : null);
        }
        return byPosition;
    }

    /**
     * 상품 삭제 요청을 처리합니다 (Kafka Consumer에서 호출).
     *
     * @param productId 삭제할 상품 ID
     */
    public void deleteProduct(UUID productId) {
        repository.deleteById(productId);
    }
}
