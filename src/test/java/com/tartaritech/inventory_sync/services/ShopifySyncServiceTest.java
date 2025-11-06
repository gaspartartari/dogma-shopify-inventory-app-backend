package com.tartaritech.inventory_sync.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.tartaritech.inventory_sync.entities.ShopifySyncOperation;
import com.tartaritech.inventory_sync.repositories.ShopifySyncOperationRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("ShopifySyncService Unit Tests")
class ShopifySyncServiceTest {

    @Mock
    private ShopifySyncOperationRepository shopifySyncOperationRepository;

    @Mock
    private ShopifyInventoryService shopifyInventoryService;

    @InjectMocks
    private ShopifySyncService shopifySyncService;

    private ShopifySyncOperation opInsert;
    private ShopifySyncOperation opDelete;
    private ShopifySyncOperation opHardDecrement;

    @BeforeEach
    void setup() {
        opInsert = buildOp("sub-1", "insert", "SKU123_size", 5, "PENDING", 0);
        opDelete = buildOp("sub-2", "delete", "SKU456_color", 3, "PENDING", 1);
        opHardDecrement = buildOp("sub-3", "hard-decrement", "SKU789_pack", 2, "PENDING", 2);
    }

    @Nested
    @DisplayName("processPendingShopifyOperations")
    class ProcessPendingOpsTests {
        @Test
        @DisplayName("Should return early when no pending operations")
        void shouldReturnEarlyWhenNoPending() {
            when(shopifySyncOperationRepository.findByStatusAndRetryCountLessThan("PENDING", 3))
                    .thenReturn(List.of());

            shopifySyncService.processPendingShopifyOperations();

            verify(shopifySyncOperationRepository, never()).save(any());
            verifyNoInteractions(shopifyInventoryService);
        }

        @Test
        @DisplayName("Should execute and mark executed on success")
        void shouldExecuteAndMarkExecuted() {
            when(shopifySyncOperationRepository.findByStatusAndRetryCountLessThan("PENDING", 3))
                    .thenReturn(List.of(opInsert));
            when(shopifyInventoryService.findVariantGidBySku("SKU123")).thenReturn("gid://item/1");

            shopifySyncService.processPendingShopifyOperations();

            ArgumentCaptor<ShopifySyncOperation> captor = ArgumentCaptor.forClass(ShopifySyncOperation.class);
            verify(shopifySyncOperationRepository).save(captor.capture());
            ShopifySyncOperation saved = captor.getValue();
            assertEquals("EXECUTED", saved.getStatus());
            assertNotNull(saved.getExecutedAt());

            verify(shopifyInventoryService).adjustReservedInventory("gid://item/1", 5, "insert");
            verify(shopifyInventoryService).adjustAvailableInventory("gid://item/1", -5, "insert");
        }

        @Test
        @DisplayName("Should increment retry and keep PENDING when will retry")
        void shouldIncrementRetryAndKeepPending() {
            when(shopifySyncOperationRepository.findByStatusAndRetryCountLessThan("PENDING", 3))
                    .thenReturn(List.of(opDelete));
            when(shopifyInventoryService.findVariantGidBySku("SKU456")).thenReturn(null); // forces failure

            shopifySyncService.processPendingShopifyOperations();

            ArgumentCaptor<ShopifySyncOperation> captor = ArgumentCaptor.forClass(ShopifySyncOperation.class);
            verify(shopifySyncOperationRepository).save(captor.capture());
            ShopifySyncOperation saved = captor.getValue();
            assertEquals(Integer.valueOf(2), saved.getRetryCount());
            assertEquals("PENDING", saved.getStatus());
            assertNotNull(saved.getErrorMessage());
        }

        @Test
        @DisplayName("Should mark FAILED after 3 attempts")
        void shouldMarkFailedAfterThreeAttempts() {
            when(shopifySyncOperationRepository.findByStatusAndRetryCountLessThan("PENDING", 3))
                    .thenReturn(List.of(opHardDecrement));
            when(shopifyInventoryService.findVariantGidBySku("SKU789")).thenReturn(null); // forces failure

            shopifySyncService.processPendingShopifyOperations();

            ArgumentCaptor<ShopifySyncOperation> captor = ArgumentCaptor.forClass(ShopifySyncOperation.class);
            verify(shopifySyncOperationRepository).save(captor.capture());
            ShopifySyncOperation saved = captor.getValue();
            assertEquals(Integer.valueOf(3), saved.getRetryCount());
            assertEquals("FAILED", saved.getStatus());
            assertNotNull(saved.getErrorMessage());
        }

        @Test
        @DisplayName("Should compute deltas per operation type")
        void shouldComputeDeltasPerOperation() {
            // insert -> reserved +5, available -5
            when(shopifySyncOperationRepository.findByStatusAndRetryCountLessThan("PENDING", 3))
                    .thenReturn(new ArrayList<>(List.of(opInsert, opDelete, opHardDecrement)));
            when(shopifyInventoryService.findVariantGidBySku(anyString()))
                    .thenReturn("gid://item/1");

            shopifySyncService.processPendingShopifyOperations();

            // verify per SKU split logic
            verify(shopifyInventoryService).findVariantGidBySku("SKU123");
            verify(shopifyInventoryService).findVariantGidBySku("SKU456");
            verify(shopifyInventoryService).findVariantGidBySku("SKU789");

            // For insert
            verify(shopifyInventoryService).adjustReservedInventory("gid://item/1", 5, "insert");
            verify(shopifyInventoryService).adjustAvailableInventory("gid://item/1", -5, "insert");
            // For delete
            verify(shopifyInventoryService, atLeastOnce()).adjustReservedInventory("gid://item/1", -3, "delete");
            verify(shopifyInventoryService, atLeastOnce()).adjustAvailableInventory("gid://item/1", 3, "delete");
            // For hard-decrement
            verify(shopifyInventoryService, atLeastOnce()).adjustReservedInventory("gid://item/1", -2, "hard-decrement");
            verify(shopifyInventoryService, atLeastOnce()).adjustAvailableInventory("gid://item/1", 0, "hard-decrement");
        }
    }

    private ShopifySyncOperation buildOp(String subId, String op, String sku, int qty, String status, int retry) {
        ShopifySyncOperation s = new ShopifySyncOperation();
        s.setSubscriptionId(subId);
        s.setOperation(op);
        s.setSku(sku);
        s.setQuantity(qty);
        s.setStatus(status);
        s.setRetryCount(retry);
        s.setCreatedAt(Instant.now());
        return s;
    }
}
