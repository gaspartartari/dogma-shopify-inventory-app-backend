package com.tartaritech.inventory_sync.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.tartaritech.inventory_sync.enums.JobStatus;
import com.tartaritech.inventory_sync.repositories.WebhookIdempotencyRepository;

@ExtendWith(MockitoExtension.class)
public class IdempotencyCleanupServiceTest {

    @Mock
    private WebhookIdempotencyRepository webhookIdempotencyRepository;

    @InjectMocks
    private IdempotencyCleanupService idempotencyCleanupService;

    @BeforeEach
    void setup() throws Exception {
        // default ttl and strategy are set by @Value with defaults in service
        // For direct methods, injection is not required
    }

    @Test
    public void cleanupConservativeShouldDeleteOnlyDoneJobsBeforeCutoff() {
        when(webhookIdempotencyRepository.deleteByCreatedAtBeforeAndJobStatus(any(Instant.class), eq(JobStatus.DONE)))
                .thenReturn(5);

        int deleted = idempotencyCleanupService.cleanupConservative();

        assertEquals(5, deleted);
        verify(webhookIdempotencyRepository)
                .deleteByCreatedAtBeforeAndJobStatus(any(Instant.class), eq(JobStatus.DONE));
    }

    @Test
    public void cleanupAggressiveShouldDeleteByCutoffTime() {
        when(webhookIdempotencyRepository.deleteByCreatedAtBefore(any(Instant.class)))
                .thenReturn(12);

        int deleted = idempotencyCleanupService.cleanupAggressive();

        assertEquals(12, deleted);
        verify(webhookIdempotencyRepository).deleteByCreatedAtBefore(any(Instant.class));
    }

    @Test
    public void cleanupExpiredIdempotencyRecordsManualShouldDeleteByCutoffTime() {
        when(webhookIdempotencyRepository.deleteByCreatedAtBefore(any(Instant.class)))
                .thenReturn(7);

        int deleted = idempotencyCleanupService.cleanupExpiredIdempotencyRecordsManual();

        assertEquals(7, deleted);
        verify(webhookIdempotencyRepository).deleteByCreatedAtBefore(any(Instant.class));
    }

    @Test
    public void serviceShouldBeCreated() {
        assertNotNull(idempotencyCleanupService);
    }
}
