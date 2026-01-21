package com.barofarm.ai.config;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
public class ElasticsearchConfig {

    /**
     * Elasticsearch RestClient 생성
     *
     * @param elasticsearchUris Elasticsearch URI (예: http://localhost:9200)
     * @return RestClient 인스턴스
     */
    @Bean
    public RestClient elasticsearchRestClient(
            @Value("${spring.elasticsearch.uris:http://localhost:9200}") String elasticsearchUris) {

        String[] uris = elasticsearchUris.split(",");
        HttpHost[] hosts = new HttpHost[uris.length];

        for (int i = 0; i < uris.length; i++) {
            String uri = uris[i].trim();
            if (uri.startsWith("http://")) {
                uri = uri.substring(7);
            } else if (uri.startsWith("https://")) {
                uri = uri.substring(8);
            }

            String[] parts = uri.split(":");
            String host = parts[0];
            int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 9200;

            hosts[i] = new HttpHost(host, port, uri.startsWith("https://") ? "https" : "http");
        }

        return RestClient.builder(hosts).build();
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
            .initializeSchema(true) // 인덱스 및 매핑 자동 생성 (dense_vector 필드 포함)
            .build();
    }
}
