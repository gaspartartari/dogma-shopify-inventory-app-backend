package com.tartaritech.inventory_sync.services;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.tartaritech.inventory_sync.entities.ShopifySyncOperation;
import com.tartaritech.inventory_sync.repositories.ShopifySyncOperationRepository;

@Service
public class ShopifySyncService {

    private final ShopifySyncOperationRepository shopifySyncOperationRepository;
    private final ShopifyInventoryService shopifyInventoryService;
    private final Logger logger = LoggerFactory.getLogger(ShopifySyncService.class);

    public ShopifySyncService(ShopifySyncOperationRepository shopifySyncOperationRepository,
                             ShopifyInventoryService shopifyInventoryService) {
        this.shopifySyncOperationRepository = shopifySyncOperationRepository;
        this.shopifyInventoryService = shopifyInventoryService;
    }

    @Scheduled(fixedDelay = 30000)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processPendingShopifyOperations() {
        logger.debug("ShopifySyncService: Checking for pending Shopify operations...");
        
        List<ShopifySyncOperation> pendingOps = shopifySyncOperationRepository
            .findByStatusAndRetryCountLessThan("PENDING", 3);
        
        if (pendingOps.isEmpty()) {
            logger.debug("ShopifySyncService: No pending Shopify operations found");
            return;
        }
        
        logger.info("ShopifySyncService: Processing {} pending Shopify operations", pendingOps.size());
        
        for (ShopifySyncOperation op : pendingOps) {
            try {
                executeShopifyOperation(op);
                op.setStatus("EXECUTED");
                op.setExecutedAt(Instant.now());
                logger.info("Shopify operation executed: {} for subscription {} SKU {}", 
                           op.getOperation(), op.getSubscriptionId(), op.getSku());
            } catch (Exception e) {
                op.setRetryCount(op.getRetryCount() + 1);
                op.setErrorMessage(e.getMessage());
                
                if (op.getRetryCount() >= 3) {
                    op.setStatus("FAILED");
                    logger.error("Shopify operation failed permanently after 3 attempts: {} for subscription {} SKU {}", 
                               op.getOperation(), op.getSubscriptionId(), op.getSku(), e);
                } else {
                    logger.warn("Shopify operation failed, will retry (attempt {}/3): {} for subscription {} SKU {}", 
                               op.getRetryCount(), op.getOperation(), op.getSubscriptionId(), op.getSku(), e);
                }
            }
            shopifySyncOperationRepository.save(op);
        }
        
        logger.info("Completed processing Shopify operations");
    }

    private void executeShopifyOperation(ShopifySyncOperation op) {
        logger.info("Executing Shopify operation: {} for SKU: {} (subscription: {})", 
                    op.getOperation(), op.getSku(), op.getSubscriptionId());

        Integer reservedInventory = prepareReservedInventory(op);
        Integer availableInventory = prepareAvailableInventory(op);

        logger.debug("Calculated inventory adjustments - Reserved: {}, Available: {}", 
                     reservedInventory, availableInventory);

        String[] arr = op.getSku().split("_");
        String shopifySku = arr[0];
        
        logger.debug("Looking up Shopify variant for SKU: {}", shopifySku);
        String shopifyGid = shopifyInventoryService.findVariantGidBySku(shopifySku);
        
        if (shopifyGid == null) {
            logger.error("Shopify variant not found for SKU: {}", shopifySku);
            throw new RuntimeException("Shopify variant not found for SKU: " + shopifySku);
        }
        
        logger.info("Found Shopify variant: {} - Adjusting inventory", shopifyGid);
        shopifyInventoryService.adjustReservedInventory(shopifyGid, reservedInventory, op.getOperation());
        shopifyInventoryService.adjustAvailableInventory(shopifyGid, availableInventory, op.getOperation());
        logger.info("Successfully adjusted Shopify inventory for SKU: {}", shopifySku);
    }

    private Integer prepareReservedInventory(ShopifySyncOperation op) {
        Integer reservedInventory;
        switch (op.getOperation()) {
            case "hard-decrement":
                    reservedInventory = op.getQuantity() * -1;
                break;
            case "delete":
                reservedInventory = op.getQuantity() * -1;
                break;
            default:
                reservedInventory = op.getQuantity();
                break;
        }
        return reservedInventory;
    }

    private Integer prepareAvailableInventory(ShopifySyncOperation op) {
        Integer availableInventory;
        switch (op.getOperation()) {
            case "hard-decrement":
                availableInventory = 0;
                break;
            case "delete":
                availableInventory = op.getQuantity();
                break;
            default:
                availableInventory = op.getQuantity() * -1;
                break;
        }
        return availableInventory;
    }
}
