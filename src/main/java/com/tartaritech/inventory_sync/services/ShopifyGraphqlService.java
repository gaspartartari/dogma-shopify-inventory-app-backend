package com.tartaritech.inventory_sync.services;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class ShopifyGraphqlService {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String graphqlUrl;
    private final String accessToken;

    public ShopifyGraphqlService(
            ObjectMapper objectMapper,
            @Value("${shopify.api.token}") String accessToken,
            @Value("${shopify.api.version}") String apiVersion,
            @Value("${shopify.store.url}") String storeUrl
    ) {
        this.objectMapper = objectMapper;
        this.accessToken = accessToken;
        this.graphqlUrl = storeUrl + "/admin/api/" + apiVersion + "/graphql.json";
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }
    
    public JsonNode executeQuery(String query, Map<String, Object> variables) {
        return executeGraphQLRequest(query, variables);
    }

    public JsonNode executeMutation(String mutation, Map<String, Object> variables) {
        return executeGraphQLRequest(mutation, variables);
    }

    private JsonNode executeGraphQLRequest(String query, Map<String, Object> variables) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("query", query);
            if (variables != null && !variables.isEmpty()) {
                requestBody.put("variables", variables);
            }

            String jsonBody = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(graphqlUrl))
                    .header("X-Shopify-Access-Token", accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode jsonResponse = objectMapper.readTree(response.body());
                
                // Check for GraphQL errors
                if (jsonResponse.has("errors")) {
                    throw new RuntimeException("GraphQL errors: " + jsonResponse.get("errors").toString());
                }
                
                return jsonResponse.get("data");
            } else {
                throw new RuntimeException("HTTP error: " + response.statusCode() + " - " + response.body());
            }

        } catch (Exception e) {
            throw new RuntimeException("Error executing GraphQL request", e);
        }
    }
}
