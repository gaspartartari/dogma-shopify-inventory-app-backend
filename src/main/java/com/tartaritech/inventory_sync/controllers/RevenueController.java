package com.tartaritech.inventory_sync.controllers;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tartaritech.inventory_sync.dtos.RevenueDataPointDTO;
import com.tartaritech.inventory_sync.services.RevenueCacheService;
import com.tartaritech.inventory_sync.services.RevenueService;

@RestController
@RequestMapping("/api/revenue")
@CrossOrigin(origins = "*")
public class RevenueController {

    private final RevenueService revenueService;
    private final RevenueCacheService revenueCacheService;
    private final Logger logger = LoggerFactory.getLogger(RevenueController.class);

    public RevenueController(RevenueService revenueService, RevenueCacheService revenueCacheService) {
        this.revenueService = revenueService;
        this.revenueCacheService = revenueCacheService;
    }

    @GetMapping("/over-time")
    public ResponseEntity<Object> getRevenueOverTime(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        try {
            // Default to last 90 days if no dates provided
            LocalDate start = startDate != null ? LocalDate.parse(startDate) : LocalDate.now().minusDays(90);
            LocalDate end = endDate != null ? LocalDate.parse(endDate) : LocalDate.now();
            
            logger.info("Fetching revenue over time from {} to {}", start, end);
            
            List<RevenueDataPointDTO> revenueData = revenueService.getRevenueOverTime(start, end);
            
            return ResponseEntity.ok(revenueData);
        } catch (Exception e) {
            logger.error("Error fetching revenue over time", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error fetching revenue data: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    @PostMapping("/cache/refresh")
    public ResponseEntity<Object> refreshCache() {
        try {
            logger.info("Manual cache refresh requested");
            
            // Start cache refresh asynchronously in a separate thread
            new Thread(() -> {
                try {
                    revenueCacheService.refreshRevenueCache();
                } catch (Exception e) {
                    logger.error("Error during manual cache refresh", e);
                }
            }).start();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Cache refresh started. This may take several minutes.");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error starting cache refresh", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error starting cache refresh: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
}

