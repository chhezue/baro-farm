package com.barofarm.sample.elasticsearch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/api/v1/es")
public class ElasticsearchTestController {

    private static final String INDEX_NAME = "sample-index";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public ElasticsearchTestController(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Value("${spring.elasticsearch.uris}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
    }

    public record EsTestRequest(String message) {}

    public record EsTestResponse(
            String status, String indexedMessage, String lastIndexedMessage, String timestamp) {}

    @PostMapping("/test")
    public ResponseEntity<EsTestResponse> indexAndRead(@RequestBody(required = false) EsTestRequest request) {
        String message =
                (request != null && request.message() != null && !request.message().isBlank())
                        ? request.message()
                        : "es-test-" + Instant.now();

        try {
            ensureIndexExists();
            indexDocument(message);
            String lastMessage = findLastMessage();

            return ResponseEntity.ok(
                    new EsTestResponse("SENT", message, lastMessage, Instant.now().toString()));
        } catch (RestClientException | IOException e) {
            return ResponseEntity.internalServerError()
                    .body(
                            new EsTestResponse(
                                    "ERROR: " + e.getClass().getSimpleName(),
                                    null,
                                    null,
                                    Instant.now().toString()));
        }
    }

    @GetMapping("/test")
    public ResponseEntity<EsTestResponse> getLastMessage() {
        try {
            String lastMessage = findLastMessage();
            return ResponseEntity.ok(
                    new EsTestResponse("OK", null, lastMessage, Instant.now().toString()));
        } catch (RestClientException | IOException e) {
            return ResponseEntity.internalServerError()
                    .body(
                            new EsTestResponse(
                                    "ERROR: " + e.getClass().getSimpleName(),
                                    null,
                                    null,
                                    Instant.now().toString()));
        }
    }

    private void ensureIndexExists() {
        String url = baseUrl + "/" + INDEX_NAME;
        // PUT index (ignore errors if it already exists)
        try {
            restTemplate.put(url, null);
        } catch (RestClientException ignored) {
            // index may already exist – safe to ignore
        }
    }

    private void indexDocument(String message) {
        String url = baseUrl + "/" + INDEX_NAME + "/_doc";

        Map<String, Object> body = new HashMap<>();
        body.put("message", message);
        body.put("timestamp", Instant.now().toString());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
    }

    private String findLastMessage() throws IOException {
        String url = baseUrl + "/" + INDEX_NAME + "/_search?size=1&sort=timestamp:desc";

        String raw = restTemplate.getForObject(url, String.class);
        if (raw == null) {
            return null;
        }

        JsonNode root = objectMapper.readTree(raw);
        JsonNode hits = root.path("hits").path("hits");
        if (!hits.isArray() || hits.isEmpty()) {
            return null;
        }

        JsonNode first = hits.get(0);
        return first.path("_source").path("message").asText(null);
    }
}
