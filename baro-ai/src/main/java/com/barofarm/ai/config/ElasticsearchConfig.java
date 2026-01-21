package com.barofarm.ai.config;

import org.elasticsearch.client.RestClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

/**
 * 제철 지식 RAG용 VectorStore 설정
 *
 * ElasticsearchVectorStore를 사용하여 벡터를 Elasticsearch의 dense_vector 필드에 영구 저장합니다.
 *
 * Dense Vector 적용 효과:
 * - Elasticsearch의 k-NN/ANN 알고리즘을 활용한 고성능 벡터 검색
 * - 대용량 데이터에서도 O(log N) 수준의 검색 성능
 * - 키워드 검색과 벡터 검색의 하이브리드 검색 지원
 * - 분산 환경에서의 확장성
 */
@Configuration
@EnableElasticsearchRepositories(basePackageClasses = com.barofarm.ai.AiApplication.class)
public class ElasticsearchConfig extends ElasticsearchConfiguration {

    @Value("${spring.elasticsearch.uris:http://localhost:9200}")
    private String elasticsearchUri;

    @Override
    public ClientConfiguration clientConfiguration() {
        // "http://elasticsearch:9200" -> "elasticsearch:9200"
        // "http://localhost:9200" -> "localhost:9200"
        String hostAndPort = elasticsearchUri
            .replace("http://", "")
            .replace("https://", "");

        return ClientConfiguration.builder()
            .connectedTo(hostAndPort)
            .withConnectTimeout(java.time.Duration.ofSeconds(10))
            .withSocketTimeout(java.time.Duration.ofSeconds(30))
            .build();
    }

    /**
     * 제철 지식 RAG용 VectorStore 생성 (Elasticsearch Dense Vector 사용)
     *
     * @param restClient     Elasticsearch RestClient
     * @param embeddingModel OpenAI EmbeddingModel
     * @return ElasticsearchVectorStore 인스턴스 (dense_vector 필드 사용)
     */
    @Bean(name = "seasonalityVectorStore")
    public VectorStore seasonalityVectorStore(
        RestClient restClient,
        @Qualifier("openAiEmbeddingModel") EmbeddingModel embeddingModel) {

        // ElasticsearchVectorStore를 사용하여 dense_vector 필드에 벡터 저장
        // initializeSchema(true): 인덱스 및 매핑 자동 생성 (dense_vector 필드 포함)
        return ElasticsearchVectorStore.builder(restClient, embeddingModel)
            .initializeSchema(true)
            .build();
    }
}
