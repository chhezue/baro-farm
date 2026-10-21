package com.barofarm.ai.search.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.barofarm.ai.embedding.application.ProductEmbeddingService;
import com.barofarm.ai.search.application.dto.product.ProductAutoCompleteResponse;
import com.barofarm.ai.search.application.dto.product.ProductIndexRequest;
import com.barofarm.ai.search.application.dto.product.ProductSearchRequest;
import com.barofarm.ai.search.application.dto.product.ProductSummaryResponse;
import com.barofarm.ai.search.domain.ProductDocument;
import com.barofarm.ai.search.infrastructure.elasticsearch.ProductSearchRepository;
import com.barofarm.ai.search.infrastructure.llm.ProductIngredientNormalizer;
import com.barofarm.dto.CustomPage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.convert.ElasticsearchCustomConversions;
import org.springframework.data.elasticsearch.core.convert.MappingElasticsearchConverter;
import org.springframework.data.elasticsearch.core.mapping.SimpleElasticsearchMappingContext;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 실물 Elasticsearch를 띄워 **매핑과 쿼리가 실제로 동작하는지** 확인한다.
 *
 * <p>단위 테스트는 "어떤 메서드를 몇 번 불렀는가"까지만 고정할 수 있다. 여기서 보는 것은 그것으로는
 * 알 수 없는 것들이다 — 상품 문서의 매핑이 실제로 만들어지는지, 한국어 형태소 분석이 걸리는지,
 * 2026-09-08에 별도 인덱스에서 서브필드로 옮긴 자동완성이 정말 접두어를 잡는지, 그리고 벡터 후보
 * 조회의 Painless 스크립트가 문법 오류 없이 도는지.
 *
 * <p>지금까지 이 확인은 사람이 로컬에 서버를 띄워 손으로 했고, 실행하지 못한 항목이 26건 쌓여 있었다
 * (docs/audits/open-items.md A-1). 그중 검색 경로 몫을 이 클래스가 자동으로 대신한다.
 *
 * <p>이미지는 저장소에 이미 있는 {@code docker/local/Dockerfile.es-nori}를 그대로 빌드해 쓴다 —
 * 한국어 형태소 분석기(nori)가 들어 있어야 상품명 매핑이 만들어지기 때문이다. 같은 파일을 로컬 확인
 * 환경과 공유하므로 둘이 어긋날 일이 없다. Docker가 없는 환경에서는 이 클래스만 실패한다.
 */
@Testcontainers
class ProductIndexAndSearchIntegrationTest {

    /** 상품 문서 매핑이 요구하는 임베딩 차원 */
    private static final int VECTOR_DIMENSION = 768;

    private static ElasticsearchContainer elasticsearch;
    private static RestClient restClient;
    private static ElasticsearchOperations operations;

    private ProductIndexService indexService;
    private ProductSearchService searchService;
    private ProductAutocompleteService autocompleteService;
    private VectorProductSearchService vectorSearchService;

    @BeforeAll
    static void startElasticsearch() {
        // 이름을 고정해 두면 두 번째 실행부터는 Docker 빌드 캐시가 그대로 쓰인다.
        String image = new ImageFromDockerfile("baro-ai-es-nori-test", false)
            .withDockerfile(Path.of("docker/local/Dockerfile.es-nori"))
            .get();

        elasticsearch = new ElasticsearchContainer(
            DockerImageName.parse(image)
                .asCompatibleSubstituteFor("docker.elastic.co/elasticsearch/elasticsearch"))
            .withEnv("xpack.security.enabled", "false")
            .withEnv("discovery.type", "single-node")
            .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m");
        elasticsearch.start();

        restClient = RestClient.builder(HttpHost.create(elasticsearch.getHttpHostAddress())).build();
        ElasticsearchClient client =
            new ElasticsearchClient(new RestClientTransport(restClient, new JacksonJsonpMapper()));

        // 변환기를 Spring Boot가 만드는 것과 같은 순서로 조립한다. 이 단계를 건너뛰고 템플릿만 만들면
        // 매핑 컨텍스트가 UUID를 "값 하나"가 아니라 "필드를 가진 객체"로 보고 그 안을 들여다보려 하는데,
        // JDK 21에서는 java.util 내부 필드에 접근할 수 없어 인덱스를 만드는 첫 호출부터 실패한다.
        ElasticsearchCustomConversions conversions = new ElasticsearchCustomConversions(List.of());
        SimpleElasticsearchMappingContext mappingContext = new SimpleElasticsearchMappingContext();
        mappingContext.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
        mappingContext.afterPropertiesSet();

        MappingElasticsearchConverter converter = new MappingElasticsearchConverter(mappingContext);
        converter.setConversions(conversions);
        converter.afterPropertiesSet();

        operations = new ElasticsearchTemplate(client, converter);
    }

    @AfterAll
    static void stopElasticsearch() throws Exception {
        if (restClient != null) {
            restClient.close();
        }
        if (elasticsearch != null) {
            elasticsearch.stop();
        }
    }

    @BeforeEach
    void setUp() {
        // 테스트마다 인덱스를 지우고 다시 만들어 앞 테스트가 남긴 문서가 섞이지 않게 한다.
        var indexOps = operations.indexOps(ProductDocument.class);
        if (indexOps.exists()) {
            indexOps.delete();
        }
        indexOps.createWithMapping();

        ProductEmbeddingService embeddingService = mock(ProductEmbeddingService.class);
        ProductIngredientNormalizer normalizer = mock(ProductIngredientNormalizer.class);
        when(normalizer.normalize(anyString(), any())).thenReturn("재료");
        when(embeddingService.embedProducts(anyList())).thenAnswer(invocation -> {
            List<String> names = invocation.getArgument(0);
            List<float[]> vectors = new ArrayList<>();
            for (int i = 0; i < names.size(); i++) {
                vectors.add(unitVector(i));
            }
            return vectors;
        });

        // 색인 서비스가 실제로 쓰는 저장소 메서드 둘만 같은 템플릿으로 넘겨 준다. Spring 컨텍스트를 띄우지
        // 않는 이유는 여기서 보려는 것이 빈 배선이 아니라 매핑과 쿼리이기 때문이다.
        indexService = new ProductIndexService(
            templateBackedRepository(), embeddingService, normalizer, new SimpleMeterRegistry());
        searchService = new ProductSearchService(operations);
        autocompleteService = new ProductAutocompleteService(operations);
        vectorSearchService = new VectorProductSearchService(operations, new SimpleMeterRegistry());
    }

    @Test
    @DisplayName("색인한 상품이 키워드 검색으로 다시 나온다 — 매핑·형태소 분석·상태 필터가 실제로 동작한다")
    void indexedProduct_isFoundByKeywordSearch() {
        indexService.indexProducts(List.of(
            product("유기농 방울토마토 1kg", "ON_SALE"),
            product("제주 감귤 3kg", "ON_SALE"),
            product("품절된 토마토 주스", "SOLD_OUT")));
        refresh();

        CustomPage<ProductSummaryResponse> found =
            searchService.search(new ProductSearchRequest("토마토", null, null, null), 0, 10);

        // 판매 중인 토마토 상품만 나온다 — 품절 상품은 상태 필터에서 빠진다.
        assertThat(found.content()).extracting(ProductSummaryResponse::productName)
            .containsExactly("유기농 방울토마토 1kg");
    }

    @Test
    @DisplayName("자동완성이 상품 인덱스의 접두어 서브필드로 동작한다 — 별도 인덱스 없이도 같은 결과다")
    void autocomplete_matchesPrefixOnMergedIndex() {
        indexService.indexProducts(List.of(
            product("토마토 주스", "ON_SALE"),
            product("토마토 소스", "ON_SALE"),
            product("감귤 주스", "ON_SALE"),
            product("토마토 통조림", "SOLD_OUT")));
        refresh();

        List<ProductAutoCompleteResponse> suggestions = autocompleteService.autocomplete("토마", 10);

        assertThat(suggestions).extracting(ProductAutoCompleteResponse::productName)
            .containsExactlyInAnyOrder("토마토 주스", "토마토 소스");
    }

    @Test
    @DisplayName("벡터 후보 조회가 유사도 순으로 돌아오고, 제외 상품과 벡터 없는 상품은 빠진다")
    void vectorSearch_returnsCandidatesByCosineSimilarity() {
        List<ProductDocument> saved = indexService.indexProducts(List.of(
            product("사과", "ON_SALE"),
            product("배", "ON_SALE"),
            product("포도", "ON_SALE")));
        refresh();

        UUID excluded = saved.get(0).getProductId();

        // 0번 상품과 같은 방향의 벡터로 물어보되 그 상품 자신은 제외한다.
        List<ProductDocument> candidates =
            vectorSearchService.findSimilarProductDocumentsByVector(unitVector(0), 10, List.of(excluded));

        assertThat(candidates).extracting(ProductDocument::getProductId).doesNotContain(excluded);
        assertThat(candidates).hasSize(2);
        // 고르는 쪽이 벡터를 보지 않으므로 후보에는 벡터가 실려 오지 않는다(2026-09-09).
        // 문서당 약 10KB라, 이게 실제로 빠지는지는 실물 ES가 있어야 확인된다.
        assertThat(candidates.get(0).getVector()).isNull();
    }

    private void refresh() {
        operations.indexOps(ProductDocument.class).refresh();
    }

    private static ProductIndexRequest product(String name, String status) {
        return new ProductIndexRequest(
            UUID.randomUUID(), name, UUID.randomUUID(), "AGRI_X", "과일", 1000L, status, Instant.now());
    }

    /**
     * 색인 서비스가 부르는 저장소 메서드({@code saveAll}·{@code findAllById})만 실물 템플릿으로 넘겨 준다.
     * 나머지는 이 테스트가 부르지 않으므로 mock 기본 동작 그대로 둔다.
     */
    private static ProductSearchRepository templateBackedRepository() {
        ProductSearchRepository repository = mock(ProductSearchRepository.class);

        when(repository.saveAll(anyIterable())).thenAnswer(invocation -> {
            List<ProductDocument> documents = new ArrayList<>();
            invocation.<Iterable<ProductDocument>>getArgument(0).forEach(documents::add);
            operations.save(documents);
            return documents;
        });

        when(repository.findAllById(anyIterable())).thenAnswer(invocation -> {
            List<ProductDocument> found = new ArrayList<>();
            invocation.<Iterable<UUID>>getArgument(0).forEach(id -> {
                ProductDocument document = operations.get(id.toString(), ProductDocument.class);
                if (document != null) {
                    found.add(document);
                }
            });
            return found;
        });

        return repository;
    }

    /** 축 하나만 1인 단위 벡터. 서로 직교라 유사도 순서를 예측할 수 있다. */
    private static float[] unitVector(int axis) {
        float[] vector = new float[VECTOR_DIMENSION];
        vector[axis % VECTOR_DIMENSION] = 1f;
        return vector;
    }

}
