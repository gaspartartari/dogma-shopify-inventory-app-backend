package com.tartaritech.inventory_sync.services;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class ShopifyInventoryService {

    private ShopifyGraphqlService shopifyGraphqlService;

    private final Logger logger = LoggerFactory.getLogger(ShopifyInventoryService.class);

    private final ObjectMapper objectMapper;

    public ShopifyInventoryService(ShopifyGraphqlService shopifyGraphqlService, ObjectMapper objectMapper) {
        this.shopifyGraphqlService = shopifyGraphqlService;
        this.objectMapper = objectMapper;
    }


    public void decreaseAvaliable(String shopifyInventoryItemId, int delta) {

        String rawInventoryId = shopifyInventoryItemId.substring(shopifyInventoryItemId.lastIndexOf("/") + 1);

        String mutation = """
                mutation inventoryAdjustQuantities($input: InventoryAdjustQuantitiesInput!) {
                        inventoryAdjustQuantities(input: $input) {
                            userErrors {
                                field
                                message
                            }
                            inventoryAdjustmentGroup {
                                createdAt
                                reason
                                referenceDocumentUri
                                changes {
                                name
                                delta
                                }
                            }
                        }
                }
                  """;

        Map<String, Object> changes = new HashMap<>();
        changes.put("delta", -delta);
        changes.put("inventoryItemId", "gid://shopify/InventoryItem/" + rawInventoryId);
        changes.put("locationId", "gid://shopify/Location/64095387781");

        Map<String, Object> input = new HashMap<>();
        input.put("reason", "correction");
        input.put("name", "available");
        input.put("changes", List.of(changes));

        Map<String, Object> variables = new HashMap<>();
        variables.put("input", input);

        try {
            JsonNode response = shopifyGraphqlService.executeMutation(mutation, variables);

            // Verificar se há erros
            if (response.has("inventoryAdjustQuantities")) {
                JsonNode userErrors = response.get("inventoryAdjustQuantities").get("userErrors");
                if (userErrors != null && userErrors.isArray() && userErrors.size() > 0) {
                    throw new RuntimeException("Erro ao ajustar estoque: " + userErrors.toString());
                }

                JsonNode adjustmentGroup = response.get("inventoryAdjustQuantities").get("inventoryAdjustmentGroup");
                if (adjustmentGroup != null) {
                    System.out.println("Estoque ajustado com sucesso. Reference: " +
                            adjustmentGroup.get("referenceDocumentUri").asText());
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Erro ao executar mutation de ajuste de estoque", e);
        }
    }

    public String findVariantGidBySku(String sku) {

        String query = """
            query getProductBySku($sku: String!) {
                products(first: 1, query: $sku) {
                    edges {
                        node {
                            id
                            variants(first: 250) {
                                edges {
                                    node {
                                        id
                                        sku
                                        inventoryItem {
                                            id
                                        }
                                    }
                        
                                }
                            }
                        }
                    }
                }
            }
        """;
        
        Map<String, Object> variables = new HashMap<>();
        variables.put("sku", "sku:" + sku);
        
        int maxRetries = 2;
        long backoffMs = 1000;
        
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                JsonNode response = shopifyGraphqlService.executeQuery(query, variables);
                
                if (response.has("products")) {
                    JsonNode products = response.get("products");
                    JsonNode edges = products.get("edges");
                    
                    if (edges.isArray() && edges.size() > 0) {
                        JsonNode firstProduct = edges.get(0).get("node");
                        JsonNode variants = firstProduct.get("variants").get("edges");
                        
                        // Buscar variant com o SKU específico
                        for (JsonNode variantEdge : variants) {
                            JsonNode variant = variantEdge.get("node");
                            String variantSku = variant.get("sku").asText();
                            
                            if (sku.equals(variantSku)) {
                                // Retornar o GID completo do GraphQL ID
                                JsonNode inventoryItem = variant.get("inventoryItem");
                                if (inventoryItem != null && inventoryItem.has("id")) {
                                    logger.info("SHOPIFY INVENTORY ITEM ID: {}", inventoryItem.get("id").asText());
                                    return inventoryItem.get("id").asText();
                                }
                            }
                        }
                    }
                }
                
                throw new RuntimeException("Variante com SKU '" + sku + "' não encontrada");
                
            } catch (Exception e) {
                if (attempt < maxRetries - 1 && (e instanceof java.net.http.HttpTimeoutException 
                        || (e.getCause() instanceof java.io.IOException))) {
                    logger.warn("Erro ao buscar variante por SKU: {}. Tentativa {}/{}. Retentando...", 
                            sku, attempt + 1, maxRetries);
                    try {
                        Thread.sleep(backoffMs * (attempt + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    throw new RuntimeException("Erro ao buscar variante por SKU: " + sku, e);
                }
            }
        }
        
        throw new RuntimeException("Erro ao buscar variante por SKU: " + sku + " após " + maxRetries + " tentativas");
    }

    public int getCurrentReservedQuantity(String shopifyInventoryItemId) {
        String rawInventoryId = shopifyInventoryItemId.substring(shopifyInventoryItemId.lastIndexOf("/") + 1);
        
        String query = """
            query getInventoryItem($id: ID!) {
                inventoryItem(id: $id) {
                    id
                    inventoryLevels(first: 10) {
                        edges {
                            node {
                                location {
                                    id
                                }
                                quantities(names: ["available", "committed", "on_hand", "reserved"]) {
                                    name
                                    quantity
                                }
                            }
                        }
                    }
                }
            }
        """;
        
        Map<String, Object> variables = new HashMap<>();
        variables.put("id", "gid://shopify/InventoryItem/" + rawInventoryId);
        
        try {
            JsonNode response = shopifyGraphqlService.executeQuery(query, variables);
            
            if (response.has("inventoryItem")) {
                JsonNode inventoryItem = response.get("inventoryItem");
                if (inventoryItem != null && inventoryItem.has("inventoryLevels")) {
                    JsonNode inventoryLevels = inventoryItem.get("inventoryLevels");
                    JsonNode edges = inventoryLevels.get("edges");
                    
                    if (edges.isArray()) {
                        for (JsonNode edge : edges) {
                            JsonNode node = edge.get("node");
                            String locationId = node.get("location").get("id").asText();
                            
                            // Verificar se é a localização correta
                            if ("gid://shopify/Location/64095387781".equals(locationId)) {
                                JsonNode quantities = node.get("quantities");
                                int available = 0, committed = 0, onHand = 0, reserved = 0;
                                
                                // Parse quantities array
                                if (quantities.isArray()) {
                                    logger.info("Array the quantidades {}",objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(quantities));
                                    for (JsonNode quantity : quantities) {
                                        String name = quantity.get("name").asText();
                                        int value = quantity.get("quantity").asInt();
                                        
                                        switch (name) {
                                            case "available" -> available = value;
                                            case "committed" -> committed = value;
                                            case "on_hand" -> onHand = value;
                                            case "reserved" -> reserved = value;
                                        }
                                    }
                                }
                                
                                logger.info("Quantidade atual de reserved: {} (onHand: {}, available: {}, committed: {}) para inventory item: {}", 
                                        reserved, onHand, available, committed, shopifyInventoryItemId);
                                return reserved;
                            }
                        }
                    }
                }
            }
            
            logger.warn("Não foi possível encontrar a quantidade de reserved para inventory item: {}", shopifyInventoryItemId);
            return 0;
            
        } catch (Exception e) {
            logger.error("Erro ao buscar quantidade de reserved para inventory item: {}", shopifyInventoryItemId, e);
            throw new RuntimeException("Erro ao buscar quantidade de reserved", e);
        }
    }

    public void increaseReserved(String inventoryItemId, int amount) {
        adjustReservedInventory(inventoryItemId, amount, "increment");
    }

    public void decreaseReserved(String inventoryItemId, int amount) {
        adjustReservedInventory(inventoryItemId, -amount, "decrement");
    }

    public void resetReserved(String inventoryItemId) {
        int currentReserved = getCurrentReservedQuantity(inventoryItemId);
        
            logger.info("Zerando estoque reserved. Quantidade atual: {}, decrementando: {}", currentReserved, currentReserved);
            adjustReservedInventory(inventoryItemId, -currentReserved, "reset");
            adjustAvailableInventory(inventoryItemId, currentReserved, "reset");
   
    }

    public void adjustAvailableInventory(String shopifyInventoryItemId, int delta, String operation) {

        String rawInventoryId = shopifyInventoryItemId.substring(shopifyInventoryItemId.lastIndexOf("/") + 1);

        String mutation = """
                mutation inventoryAdjustQuantities($input: InventoryAdjustQuantitiesInput!) {
                        inventoryAdjustQuantities(input: $input) {
                            userErrors {
                                field
                                message
                            }
                            inventoryAdjustmentGroup {
                                createdAt
                                reason
                                referenceDocumentUri
                                changes {
                                    name
                                    delta
                                }
                            }
                        }
                }
                  """;

        Map<String, Object> changes = new HashMap<>();
        changes.put("delta", delta);
        changes.put("inventoryItemId", "gid://shopify/InventoryItem/" + rawInventoryId);
        changes.put("locationId", "gid://shopify/Location/64095387781");
       

        Map<String, Object> input = new HashMap<>();
        input.put("reason", "correction");
        input.put("name", "available");
        input.put("changes", List.of(changes));

        Map<String, Object> variables = new HashMap<>();
        variables.put("input", input);

        int maxRetries = 2;
        long backoffMs = 1000;
        
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                JsonNode response = shopifyGraphqlService.executeMutation(mutation, variables);

                // Verificar se há erros
                if (response.has("inventoryAdjustQuantities")) {
                    JsonNode userErrors = response.get("inventoryAdjustQuantities").get("userErrors");
                    if (userErrors != null && userErrors.isArray() && userErrors.size() > 0) {
                        throw new RuntimeException("Erro ao ajustar estoque available: " + userErrors.toString());
                    }

                    JsonNode adjustmentGroup = response.get("inventoryAdjustQuantities").get("inventoryAdjustmentGroup");
                    if (adjustmentGroup != null) {
                        JsonNode referenceUri = adjustmentGroup.get("referenceDocumentUri");
                        String reference = (referenceUri != null) ? referenceUri.asText() : "N/A";
                        logger.info("Estoque available ajustado com sucesso. Operation: {}, Delta: {}, Reference: {}",
                                operation, delta, reference);
                    }
                }
                return; // Success, exit retry loop
                
            } catch (Exception e) {
                if (attempt < maxRetries - 1 && (e instanceof java.net.http.HttpTimeoutException 
                        || (e.getCause() instanceof java.io.IOException))) {
                    logger.warn("Erro ao executar mutation de ajuste de estoque available. Tentativa {}/{}. Retentando...", 
                            attempt + 1, maxRetries);
                    try {
                        Thread.sleep(backoffMs * (attempt + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    throw new RuntimeException("Erro ao executar mutation de ajuste de estoque available", e);
                }
            }
        }
    }

    public void adjustReservedInventory(String shopifyInventoryItemId, int delta, String operation) {

        String rawInventoryId = shopifyInventoryItemId.substring(shopifyInventoryItemId.lastIndexOf("/") + 1);
        
        String mutation = """
                mutation inventoryAdjustQuantities($input: InventoryAdjustQuantitiesInput!) {
                        inventoryAdjustQuantities(input: $input) {
                            userErrors {
                                field
                                message
                            }
                            inventoryAdjustmentGroup {
                                createdAt
                                reason
                                referenceDocumentUri
                                changes {
                                    name
                                    delta
                                }
                            }
                        }
                }
                  """;

        Map<String, Object> changes = new HashMap<>();
        changes.put("delta", delta);
        changes.put("inventoryItemId", "gid://shopify/InventoryItem/" + rawInventoryId);
        changes.put("locationId", "gid://shopify/Location/64095387781");
        changes.put("ledgerDocumentUri", "gid://inventory-sync-app/Ledger/" + System.currentTimeMillis() + "-" + operation);

        Map<String, Object> input = new HashMap<>();
        input.put("reason", "correction");
        input.put("name", "reserved");
        input.put("referenceDocumentUri", "gid://inventory-sync-app/ManualAdjustment/" + System.currentTimeMillis() + "-" + operation);
        input.put("changes", List.of(changes));

        Map<String, Object> variables = new HashMap<>();
        variables.put("input", input);

        try {
            JsonNode response = shopifyGraphqlService.executeMutation(mutation, variables);

            // Verificar se há erros
            if (response.has("inventoryAdjustQuantities")) {
                JsonNode userErrors = response.get("inventoryAdjustQuantities").get("userErrors");
                if (userErrors != null && userErrors.isArray() && userErrors.size() > 0) {
                    throw new RuntimeException("Erro ao ajustar estoque reserved: " + userErrors.toString());
                }

                JsonNode adjustmentGroup = response.get("inventoryAdjustQuantities").get("inventoryAdjustmentGroup");
                if (adjustmentGroup != null) {
                    JsonNode referenceUri = adjustmentGroup.get("referenceDocumentUri");
                    String reference = (referenceUri != null) ? referenceUri.asText() : "N/A";
                    logger.info("Estoque reserved ajustado com sucesso. Operation: {}, Delta: {}, Reference: {}", 
                            operation, delta, reference);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Erro ao executar mutation de ajuste de estoque reserved", e);
        }
    }

}
