package com.tartaritech.inventory_sync.services;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tartaritech.inventory_sync.dtos.OrderDTO;
import com.tartaritech.inventory_sync.dtos.ProductDTO;
import com.tartaritech.inventory_sync.dtos.SubscriptionDTO;
import com.tartaritech.inventory_sync.dtos.SubscriptionShortDTO;
import com.tartaritech.inventory_sync.dtos.SubscriptionsDTO;
import com.tartaritech.inventory_sync.entities.RevenueCache;
import com.tartaritech.inventory_sync.repositories.ControlledSkuRepository;
import com.tartaritech.inventory_sync.repositories.RevenueCacheRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class RevenueCacheService {

    private final PagBrasilService pagBrasilService;
    private final ControlledSkuRepository controlledSkuRepository;
    private final RevenueCacheRepository revenueCacheRepository;
    
    private final Logger logger = LoggerFactory.getLogger(RevenueCacheService.class);
    
    private static final DateTimeFormatter INPUT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter OUTPUT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
    
    @Value("${revenue.cache.parallel.batch.size:20}")
    private int parallelBatchSize;
    
    @Value("${revenue.cache.enabled:true}")
    private boolean cacheEnabled;
    
    @Value("${pagbrasil.request.delay:200}")
    private int requestDelayMs;
    
    @Value("${pagbrasil.status.delay:500}")
    private int statusDelayMs;

    public RevenueCacheService(PagBrasilService pagBrasilService,
                               ControlledSkuRepository controlledSkuRepository,
                               RevenueCacheRepository revenueCacheRepository) {
        this.pagBrasilService = pagBrasilService;
        this.controlledSkuRepository = controlledSkuRepository;
        this.revenueCacheRepository = revenueCacheRepository;
    }

    /**
     * Scheduled job that runs every hour to refresh revenue cache
     */
    @Scheduled(cron = "${revenue.cache.schedule.cron:0 0 2 * * *}")
    // @Scheduled(initialDelay = 100000)
    @Transactional
    public void scheduledRefreshCache() {
        if (!cacheEnabled) {
            logger.info("Revenue cache is disabled. Skipping scheduled refresh.");
            return;
        }
        
        logger.info("Starting scheduled revenue cache refresh");
        refreshRevenueCache();
    }

    /**
     * Main method to refresh revenue cache
     * Can be called manually or by scheduled job
     */
    @Transactional
    public void refreshRevenueCache() {
        Instant startTime = Instant.now();
        logger.info("=== Starting Revenue Cache Refresh ===");
        
        try {
            // Step 1: Fetch all subscription IDs from all statuses
            logger.info("Step 1: Fetching all subscription IDs from PagBrasil");
            List<SubscriptionShortDTO> allSubscriptionIds = fetchAllSubscriptionIds();
            logger.info("Found {} total subscriptions across all statuses", allSubscriptionIds.size());
            
            if (allSubscriptionIds.isEmpty()) {
                logger.warn("No subscriptions found. Skipping cache refresh.");
                return;
            }
            
            // Step 2: Fetch full subscription details in parallel batches
            logger.info("Step 2: Fetching full subscription details (batch size: {})", parallelBatchSize);
            List<SubscriptionDTO> fullSubscriptions = fetchSubscriptionDetailsInParallel(allSubscriptionIds);
            logger.info("Successfully fetched {} full subscription details", fullSubscriptions.size());
            
            // Step 3: Calculate revenue by month
            logger.info("Step 3: Calculating revenue by month from paid orders with controlled SKUs");
            Map<String, BigDecimal> revenueByMonth = calculateRevenueByMonth(fullSubscriptions);
            logger.info("Calculated revenue for {} months", revenueByMonth.size());
            
            // Step 4: Clear old cache and save new data
            logger.info("Step 4: Updating cache in database");
            updateCacheInDatabase(revenueByMonth);
            
            Duration elapsed = Duration.between(startTime, Instant.now());
            logger.info("=== Revenue Cache Refresh Complete ===");
            logger.info("Total time: {} seconds", elapsed.getSeconds());
            logger.info("Processed {} subscriptions, cached {} months", 
                fullSubscriptions.size(), revenueByMonth.size());
            
        } catch (Exception e) {
            logger.error("Error during revenue cache refresh", e);
            logger.warn("Cache refresh failed. Previous cache data will be retained.");
        }
    }

    /**
     * Fetch all subscription IDs from all statuses (1-6)
     */
    private List<SubscriptionShortDTO> fetchAllSubscriptionIds() {
        List<SubscriptionShortDTO> allSubscriptions = new ArrayList<>();
        
        // Fetch subscriptions for each status (1-6)
        String[] statuses = {"1", "2", "3", "4", "5", "6"};
        
        for (String status : statuses) {
            try {
                logger.debug("Fetching subscriptions with status: {}", status);
                SubscriptionsDTO result = pagBrasilService.fetchSubscriptionsByStatus(status);
                
                if (result != null && result.getSubscriptions() != null) {
                    int count = result.getSubscriptions().size();
                    allSubscriptions.addAll(result.getSubscriptions());
                    logger.debug("Status {}: found {} subscriptions", status, count);
                }
                
                // Add delay between status queries to avoid rate limiting
                if (!status.equals("6")) { // Don't delay after last status
                    Thread.sleep(statusDelayMs);
                }
            } catch (InterruptedException e) {
                logger.warn("Delay interrupted while fetching status {}", status);
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                logger.error("Error fetching subscriptions for status {}: {}", status, e.getMessage());
            }
        }
        
        return allSubscriptions;
    }

    /**
     * Fetch full subscription details in parallel batches using WebFlux
     */
    private List<SubscriptionDTO> fetchSubscriptionDetailsInParallel(List<SubscriptionShortDTO> subscriptionIds) {
        return Flux.fromIterable(subscriptionIds)
            .delayElements(Duration.ofMillis(requestDelayMs)) // Configurable delay between requests to avoid rate limiting
            .flatMap(shortDto -> 
                Mono.fromCallable(() -> {
                    try {
                        return pagBrasilService.fetchSubscriptionById(shortDto);
                    } catch (Exception e) {
                        logger.warn("Failed to fetch subscription {}: {}", 
                            shortDto.getSubscription(), e.getMessage());
                        return null;
                    }
                })
                .timeout(Duration.ofSeconds(30))
                .onErrorResume(e -> {
                    logger.warn("Timeout or error fetching subscription {}", 
                        shortDto.getSubscription());
                    return Mono.empty();
                }),
                parallelBatchSize // Concurrency limit (default: 5)
            )
            .filter(dto -> dto != null)
            .collectList()
            .block();
    }

    /**
     * Calculate revenue by month from all subscriptions
     * Only includes paid orders with controlled SKUs
     */
    private Map<String, BigDecimal> calculateRevenueByMonth(List<SubscriptionDTO> subscriptions) {
        Map<String, BigDecimal> revenueByMonth = new HashMap<>();
        
        int totalOrders = 0;
        int paidOrders = 0;
        int ordersWithControlledSkus = 0;
        
        for (SubscriptionDTO subscription : subscriptions) {
            if (subscription.getOrders() == null || subscription.getOrders().isEmpty()) {
                continue;
            }
            
            // Iterate through ALL orders (don't call processOrders() which filters)
            for (OrderDTO order : subscription.getOrders()) {
                totalOrders++;
                
                // Skip orders without payment date (not paid yet)
                if (order.getPaymentDate() == null || order.getPaymentDate().trim().isEmpty()) {
                    continue;
                }
                
                paidOrders++;
                
                // Parse payment date
                LocalDate paymentDate = parsePaymentDate(order.getPaymentDate());
                if (paymentDate == null) {
                    logger.debug("Could not parse payment date for order {}: {}", 
                        order.getOrder(), order.getPaymentDate());
                    continue;
                }
                
                // Extract year-month
                String yearMonth = paymentDate.format(OUTPUT_FORMATTER);
                
                // Calculate revenue from products with controlled SKUs
                BigDecimal orderRevenue = BigDecimal.ZERO;
                boolean hasControlledSku = false;
                
                if (order.getProducts() != null) {
                    for (ProductDTO product : order.getProducts()) {
                        // Check if this is a controlled SKU
                        if (product.getSku() != null && controlledSkuRepository.existsById(product.getSku())) {
                            hasControlledSku = true;
                            
                            // Parse total price (comes as String from API)
                            BigDecimal productPrice = parseTotalPrice(product.getTotalPrice());
                            if (productPrice != null) {
                                orderRevenue = orderRevenue.add(productPrice);
                            }
                        }
                    }
                }
                
                if (hasControlledSku) {
                    ordersWithControlledSkus++;
                    // Add to monthly total
                    revenueByMonth.merge(yearMonth, orderRevenue, BigDecimal::add);
                }
            }
        }
        
        logger.info("Revenue calculation summary:");
        logger.info("  Total orders processed: {}", totalOrders);
        logger.info("  Paid orders: {}", paidOrders);
        logger.info("  Orders with controlled SKUs: {}", ordersWithControlledSkus);
        
        return revenueByMonth;
    }

    /**
     * Parse payment date from various formats
     */
    private LocalDate parsePaymentDate(String paymentDateStr) {
        try {
            // Try standard format first (yyyy-MM-dd)
            return LocalDate.parse(paymentDateStr, INPUT_FORMATTER);
        } catch (DateTimeParseException e) {
            try {
                // Try ISO format
                return LocalDate.parse(paymentDateStr);
            } catch (DateTimeParseException ex) {
                // Try with just the date part if it contains time
                if (paymentDateStr.contains("T")) {
                    String datePart = paymentDateStr.split("T")[0];
                    try {
                        return LocalDate.parse(datePart);
                    } catch (DateTimeParseException exc) {
                        return null;
                    }
                }
                return null;
            }
        }
    }

    /**
     * Parse total price from String to BigDecimal
     */
    private BigDecimal parseTotalPrice(String totalPriceStr) {
        if (totalPriceStr == null || totalPriceStr.trim().isEmpty()) {
            return null;
        }
        
        try {
            // Remove any currency symbols or commas
            String cleaned = totalPriceStr.replaceAll("[^0-9.]", "");
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            logger.warn("Could not parse total price: {}", totalPriceStr);
            return null;
        }
    }

    /**
     * Update cache in database (transactional)
     */
    @Transactional
    protected void updateCacheInDatabase(Map<String, BigDecimal> revenueByMonth) {
        // Delete all existing cache entries
        revenueCacheRepository.deleteAll();
        logger.info("Cleared old cache entries");
        
        // Create new cache entries
        List<RevenueCache> cacheEntries = revenueByMonth.entrySet().stream()
            .map(entry -> RevenueCache.create(entry.getKey(), entry.getValue()))
            .collect(Collectors.toList());
        
        // Save all at once
        revenueCacheRepository.saveAll(cacheEntries);
        logger.info("Saved {} new cache entries", cacheEntries.size());
    }
}

