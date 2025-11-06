package com.tartaritech.inventory_sync.controllers;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tartaritech.inventory_sync.services.ShopifyInventoryService;

@RestController
@RequestMapping("/api/inventory")
@CrossOrigin(origins = "*") // Permitir CORS para testes
public class InventoryManagementController {

    private final ShopifyInventoryService shopifyInventoryService;
    private final Logger logger = LoggerFactory.getLogger(InventoryManagementController.class);

    public InventoryManagementController(ShopifyInventoryService shopifyInventoryService) {
        this.shopifyInventoryService = shopifyInventoryService;
    }

    /**
     * Consultar a quantidade atual de reserved de um item
     * GET /api/inventory/reserved/current/{inventoryItemId}
     */
    @GetMapping("/reserved/current/{inventoryItemId}")
    public ResponseEntity<Map<String, Object>> getCurrentReservedQuantity(@PathVariable String inventoryItemId) {
        try {
            logger.info("Consultando quantidade atual de reserved para inventory item: {}", inventoryItemId);
            
            int currentQuantity = shopifyInventoryService.getCurrentReservedQuantity(inventoryItemId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("inventoryItemId", inventoryItemId);
            response.put("currentReservedQuantity", currentQuantity);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Erro ao consultar quantidade de reserved para inventory item: {}", inventoryItemId, e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Erro ao consultar quantidade de reserved: " + e.getMessage());
            errorResponse.put("inventoryItemId", inventoryItemId);
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Zerar o estoque reserved de um item específico
     * POST /api/inventory/reserved/reset/{inventoryItemId}
     */
    @PostMapping("/reserved/reset/{inventoryItemId}")
    public ResponseEntity<Map<String, Object>> resetReservedInventory(@PathVariable String inventoryItemId) {
        try {
            logger.info("Resetando estoque reserved para inventory item: {}", inventoryItemId);
            
            // Buscar quantidade atual e zerar
            int currentQuantity = shopifyInventoryService.getCurrentReservedQuantity(inventoryItemId);
            shopifyInventoryService.resetReserved(inventoryItemId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Estoque reserved zerado com sucesso");
            response.put("inventoryItemId", inventoryItemId);
            response.put("previousQuantity", currentQuantity);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Erro ao ajustar estoque reserved/available para inventory item: {}", inventoryItemId, e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Erro ao zerar estoque reserved: " + e.getMessage());
            errorResponse.put("inventoryItemId", inventoryItemId);
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Decrementar o estoque reserved em X unidades
     * POST /api/inventory/reserved/decrease/{inventoryItemId}?amount=X
     */
    @PostMapping("/reserved/decrease/{inventoryItemId}")
    public ResponseEntity<Map<String, Object>> decreaseReservedInventory(
            @PathVariable String inventoryItemId,
            @RequestParam int amount) {
        try {
            logger.info("Decrementando estoque reserved em {} unidades para inventory item: {}", amount, inventoryItemId);
            
            shopifyInventoryService.decreaseReserved(inventoryItemId, amount);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", String.format("Estoque reserved decrementado em %d unidades", amount));
            response.put("inventoryItemId", inventoryItemId);
            response.put("amount", amount);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Erro ao decrementar estoque reserved para inventory item: {}", inventoryItemId, e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Erro ao decrementar estoque reserved: " + e.getMessage());
            errorResponse.put("inventoryItemId", inventoryItemId);
            errorResponse.put("amount", amount);
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Incrementar o estoque reserved em X unidades
     * POST /api/inventory/reserved/increase/{inventoryItemId}?amount=X
     */
    @PostMapping("/reserved/increase/{inventoryItemId}")
    public ResponseEntity<Map<String, Object>> increaseReservedInventory(
            @PathVariable String inventoryItemId,
            @RequestParam int amount) {
        try {
            logger.info("Incrementando estoque reserved em {} unidades para inventory item: {}", amount, inventoryItemId);
            
            shopifyInventoryService.increaseReserved(inventoryItemId, amount);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", String.format("Estoque reserved incrementado em %d unidades", amount));
            response.put("inventoryItemId", inventoryItemId);
            response.put("amount", amount);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Erro ao incrementar estoque reserved para inventory item: {}", inventoryItemId, e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Erro ao incrementar estoque reserved: " + e.getMessage());
            errorResponse.put("inventoryItemId", inventoryItemId);
            errorResponse.put("amount", amount);
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
}
