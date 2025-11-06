package com.tartaritech.inventory_sync.services;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;

import reactor.core.publisher.Mono;

@Service
public class ShopifyGraphqlService {

    private final WebClient webClient;

    public ShopifyGraphqlService(
        WebClient webClient,
        @Value("${shopify.api.token}") String accessToken,
        @Value("${shopify.api.version}") String apiVersion,
        @Value("${shopify.store.url}") String storeUrl
    ) {

        this.webClient = webClient.mutate()
            .baseUrl(storeUrl + "/admin/api/" + apiVersion + "/graphql.json")
            .defaultHeader("X-Shopify-Access-Token", accessToken)
            .defaultHeader("Content-Type", "application/json")
            .build();

    }
    
    public Mono<JsonNode> executeQuery(String query, Map<String, Object> variables) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("query", query);
        if (variables != null && !variables.isEmpty()) {
            requestBody.put("variables", variables);
        }

        return webClient.post()
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(response -> {
                    // Check for GraphQL errors
                    if (response.has("errors")) {
                        throw new RuntimeException("GraphQL errors: " + response.get("errors").toString());
                    }
                    return response.get("data");
                });
    }

    public Mono<JsonNode> executeMutation(String mutation, Map<String, Object> variables) {
        return executeQuery(mutation, variables);
    }



    
}
