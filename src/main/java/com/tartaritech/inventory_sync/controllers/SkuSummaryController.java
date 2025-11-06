package com.tartaritech.inventory_sync.controllers;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tartaritech.inventory_sync.dtos.SkuSummaryDTO;
import com.tartaritech.inventory_sync.services.SkuSummaryService;

@RestController
@RequestMapping("/api/sku-summary")
@CrossOrigin(origins = "*")
public class SkuSummaryController {

    private final SkuSummaryService skuSummaryService;
    private final Logger logger = LoggerFactory.getLogger(SkuSummaryController.class);

    public SkuSummaryController(SkuSummaryService skuSummaryService) {
        this.skuSummaryService = skuSummaryService;
    }

    @GetMapping
    public ResponseEntity<List<SkuSummaryDTO>> getAllSkuSummaries() {
        try {
            logger.info("Request received to fetch all SKU summaries");
            List<SkuSummaryDTO> summaries = skuSummaryService.getAllSkuSummaries();
            return ResponseEntity.ok(summaries);
        } catch (Exception e) {
            logger.error("Error fetching SKU summaries", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}

