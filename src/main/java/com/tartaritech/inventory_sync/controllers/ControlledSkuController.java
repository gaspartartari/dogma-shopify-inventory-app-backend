package com.tartaritech.inventory_sync.controllers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tartaritech.inventory_sync.dtos.ControlledSkuDTO;
import com.tartaritech.inventory_sync.services.ControlledSkuService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/controlled-skus")
@CrossOrigin(origins = "*")
@Validated
public class ControlledSkuController {

    private final ControlledSkuService controlledSkuService;
    private final Logger logger = LoggerFactory.getLogger(ControlledSkuController.class);

    public ControlledSkuController(ControlledSkuService controlledSkuService) {
        this.controlledSkuService = controlledSkuService;
    }

    @GetMapping
    public ResponseEntity<List<ControlledSkuDTO>> getAllControlledSkus() {
        try {
            logger.info("Request to get all controlled SKUs");
            List<ControlledSkuDTO> skus = controlledSkuService.getAllControlledSkus();
            return ResponseEntity.ok(skus);
        } catch (Exception e) {
            logger.error("Error fetching all controlled SKUs", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{sku}")
    public ResponseEntity<?> getControlledSkuBySku(@PathVariable String sku) {
        try {
            logger.info("Request to get controlled SKU: {}", sku);
            Optional<ControlledSkuDTO> skuOpt = controlledSkuService.getControlledSkuBySku(sku);
            if (skuOpt.isPresent()) {
                return ResponseEntity.ok(skuOpt.get());
            } else {
                Map<String, String> error = new HashMap<>();
                error.put("message", "SKU não encontrado: " + sku);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
            }
        } catch (Exception e) {
            logger.error("Error fetching controlled SKU: {}", sku, e);
            Map<String, String> error = new HashMap<>();
            error.put("message", "Erro ao buscar SKU: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @PostMapping
    public ResponseEntity<?> createControlledSku(@Valid @RequestBody ControlledSkuDTO dto) {
        try {
            logger.info("Request to create controlled SKU: {}", dto.getSku());
            ControlledSkuDTO created = controlledSkuService.createControlledSku(dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            logger.warn("Validation error creating controlled SKU: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        } catch (Exception e) {
            logger.error("Error creating controlled SKU", e);
            Map<String, String> error = new HashMap<>();
            error.put("message", "Erro ao criar SKU: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @PutMapping("/{sku}")
    public ResponseEntity<?> updateControlledSku(@PathVariable String sku, @Valid @RequestBody ControlledSkuDTO dto) {
        try {
            logger.info("Request to update controlled SKU: {}", sku);
            ControlledSkuDTO updated = controlledSkuService.updateControlledSku(sku, dto);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            logger.warn("Validation error updating controlled SKU: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        } catch (Exception e) {
            logger.error("Error updating controlled SKU: {}", sku, e);
            Map<String, String> error = new HashMap<>();
            error.put("message", "Erro ao atualizar SKU: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @DeleteMapping("/{sku}")
    public ResponseEntity<?> deleteControlledSku(@PathVariable String sku) {
        try {
            logger.info("Request to delete controlled SKU: {}", sku);
            controlledSkuService.deleteControlledSku(sku);
            Map<String, String> response = new HashMap<>();
            response.put("message", "SKU deletado com sucesso");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.warn("Validation error deleting controlled SKU: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        } catch (Exception e) {
            logger.error("Error deleting controlled SKU: {}", sku, e);
            Map<String, String> error = new HashMap<>();
            error.put("message", "Erro ao deletar SKU: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
}

