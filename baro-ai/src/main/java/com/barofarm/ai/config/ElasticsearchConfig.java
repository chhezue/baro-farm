package com.barofarm.ai.config;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

/**
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
public class ElasticsearchConfig {

    @Value("${spring.elasticsearch.uris:http://localhost:9200}")
    private String elasticsearchUris;

    /**
     * Elasticsearch RestClient 생성
     *
     * @return RestClient 인스턴스
     */
    @Bean
    public RestClient elasticsearchRestClient() {
        String[] uris = elasticsearchUris.split(",");
        HttpHost[] hosts = new HttpHost[uris.length];

        for (int i = 0; i < uris.length; i++) {
            String uri = uris[i].trim();
            String scheme = "http";
            if (uri.startsWith("http://")) {
                uri = uri.substring(7);
            } else if (uri.startsWith("https://")) {
                uri = uri.substring(8);
                scheme = "https";
            }

            String[] parts = uri.split(":");
            String host = parts[0];
            int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 9200;

            hosts[i] = new HttpHost(host, port, scheme);
        }

        return RestClient.builder(hosts).build();
    }

    /**
     * ClientConfiguration Bean 생성 Spring Boot가 이를 사용하여 ElasticsearchOperations를 자동 생성합니다.
     *
     * @return ClientConfiguration 인스턴스
     */
    @Bean
    public ClientConfiguration elasticsearchClientConfiguration() {
        String[] uris = elasticsearchUris.split(",");
        String firstUri = uris[0].trim();
        String hostAndPort = firstUri
            .replace("http://", "")
            .replace("https://", "");

        return ClientConfiguration.builder()
            .connectedTo(hostAndPort)
            .withConnectTimeout(java.time.Duration.ofSeconds(10))
            .withSocketTimeout(java.time.Duration.ofSeconds(30))
            .build();
    }

}
