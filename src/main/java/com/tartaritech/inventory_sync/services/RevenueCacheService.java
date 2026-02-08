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

import com.tartaritech.inventory_sync.dtos.ProductDTO;
import com.tartaritech.inventory_sync.dtos.RecurrenceDTO;
import com.tartaritech.inventory_sync.dtos.SubscriptionFullDTO;
import com.tartaritech.inventory_sync.dtos.SubscriptionShortDTO;
import com.tartaritech.inventory_sync.dtos.SubscriptionsDTO;
import com.tartaritech.inventory_sync.entities.RevenueCache;
import com.tartaritech.inventory_sync.repositories.ControlledSkuRepository;
import com.tartaritech.inventory_sync.repositories.RevenueCacheRepository;

@Service
public class RevenueCacheService {

    private final PagBrasilService pagBrasilService;
    private final ControlledSkuRepository controlledSkuRepository;
    private final RevenueCacheRepository revenueCacheRepository;
    
    private final Logger logger = LoggerFactory.getLogger(RevenueCacheService.class);
    
    private static final DateTimeFormatter INPUT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter OUTPUT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
    
    @Value("${revenue.cache.enabled:true}")
    private boolean cacheEnabled;
    
    @Value("${pagbrasil.request.delay:2000}")
    private int requestDelayMs;
    
    @Value("${pagbrasil.status.delay:2000}")
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
    // @Scheduled(initialDelay = 500000) // 5 minutes
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
        try {
            if (!pagBrasilService.tryAcquireApiLockWithTimeout(30)) {
                logger.warn("PagBrasil API lock not available after 30s wait. Skipping cache refresh.");
                return;
            }
        } catch (InterruptedException e) {
            logger.warn("Interrupted while waiting for PagBrasil API lock. Skipping cache refresh.");
            Thread.currentThread().interrupt();
            return;
        }

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
            
            // Step 2: Fetch full subscription details sequentially
            logger.info("Step 2: Fetching full subscription details sequentially");
            List<SubscriptionFullDTO> fullSubscriptions = fetchSubscriptionDetailsInParallel(allSubscriptionIds);
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
        } finally {
            pagBrasilService.releaseApiLock();
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
                
                // Add delay between status queries to avoid rate limiting (with jitter)
                if (!status.equals("6")) { // Don't delay after last status
                    Thread.sleep((long) (statusDelayMs * (0.5 + Math.random() * 0.5)));
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
     * Fetch full subscription details sequentially with rate limiting
     */
    private List<SubscriptionFullDTO> fetchSubscriptionDetailsInParallel(List<SubscriptionShortDTO> subscriptionIds) {
        List<SubscriptionFullDTO> results = new ArrayList<>();
        
        logger.info("Fetching {} subscription details sequentially with {}ms delay between requests", 
                subscriptionIds.size(), requestDelayMs);
        
        for (int i = 0; i < subscriptionIds.size(); i++) {
            SubscriptionShortDTO shortDto = subscriptionIds.get(i);
            
            try {
                SubscriptionFullDTO dto = pagBrasilService.fetchSubscriptionById(shortDto);
                if (dto != null) {
                    results.add(dto);
                }
                
                // Add delay between requests to avoid rate limiting (with jitter: 50-100% of delay)
                if (i < subscriptionIds.size() - 1) {
                    long jitter = (long) (requestDelayMs * (0.5 + Math.random() * 0.5));
                    Thread.sleep(jitter);
                }
            } catch (InterruptedException e) {
                logger.warn("Delay interrupted while fetching subscription {}", 
                        shortDto.getSubscription());
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.warn("Failed to fetch subscription {}: {}", 
                        shortDto.getSubscription(), e.getMessage());
                // Continue with next subscription instead of failing entire batch
            }
        }
        
        logger.info("Successfully fetched {} out of {} subscription details", 
                results.size(), subscriptionIds.size());
        return results;
    }

    /**
     * Calculate revenue by month from all subscriptions
     * Only includes paid orders with controlled SKUs
     */
    private Map<String, BigDecimal> calculateRevenueByMonth(List<SubscriptionFullDTO> subscriptions) {
        Map<String, BigDecimal> revenueByMonth = new HashMap<>();
        
        int totalOrders = 0;
        int paidOrders = 0;
        int ordersWithControlledSkus = 0;
        
        for (SubscriptionFullDTO subscription : subscriptions) {
            if (subscription.getRecurrences() == null || subscription.getRecurrences().isEmpty()) {
                continue;
            }
            
            // Iterate through ALL recurrences
            for (RecurrenceDTO recurrence : subscription.getRecurrences()) {
                totalOrders++;
                
                // Skip recurrences without payment date (not paid yet)
                if (recurrence.getPaymentDate() == null || recurrence.getPaymentDate().trim().isEmpty()) {
                    continue;
                }
                
                paidOrders++;
                
                // Parse payment date
                LocalDate paymentDate = parsePaymentDate(recurrence.getPaymentDate());
                if (paymentDate == null) {
                    logger.debug("Could not parse payment date for order {}: {}", 
                        recurrence.getOrder(), recurrence.getPaymentDate());
                    continue;
                }
                
                // Extract year-month
                String yearMonth = paymentDate.format(OUTPUT_FORMATTER);
                
                // Calculate revenue from products with controlled SKUs
                BigDecimal orderRevenue = BigDecimal.ZERO;
                boolean hasControlledSku = false;
                
                if (recurrence.getProducts() != null) {
                    for (ProductDTO product : recurrence.getProducts()) {
                        // Check if this is a controlled SKU
                        if (product.getSku() != null && controlledSkuRepository.existsById(product.getSku())) {
                            hasControlledSku = true;
                            
                            // Parse total price (comes as String from API)
                            BigDecimal productPrice = parseTotalPrice(product.getAmountTotal());
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

