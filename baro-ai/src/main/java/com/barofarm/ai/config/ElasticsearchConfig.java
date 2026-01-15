package com.barofarm.ai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

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
}
