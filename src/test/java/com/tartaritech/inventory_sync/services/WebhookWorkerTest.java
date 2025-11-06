package com.tartaritech.inventory_sync.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tartaritech.inventory_sync.entities.WebhookIdempotency;
import com.tartaritech.inventory_sync.entities.WebhookJob;
import com.tartaritech.inventory_sync.enums.JobStatus;
import com.tartaritech.inventory_sync.repositories.OrderRepository;
import com.tartaritech.inventory_sync.repositories.ProductRepository;
import com.tartaritech.inventory_sync.repositories.ShopifySyncOperationRepository;
import com.tartaritech.inventory_sync.repositories.SubscriptionRepository;
import com.tartaritech.inventory_sync.repositories.WebhookIdempotencyRepository;
import com.tartaritech.inventory_sync.repositories.WebhookJobRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebhookWorker Unit Tests")
class WebhookWorkerTest {

    @Mock private WebhookJobRepository webhookJobRepository;
    @Mock private WebhookIdempotencyRepository webhookIdempotencyRepository;
    @Mock private WebhookJobService webhookJobService;
    @Mock private ObjectMapper objectMapper;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private ProductRepository productRepository;
    @Mock private ShopifySyncOperationRepository shopifySyncOperationRepository;
    @Mock private OrderRepository orderRepository;

    @InjectMocks private WebhookWorker webhookWorker;

    @BeforeEach
    void configureDefaults() {
        // Make retries small to test quickly
        ReflectionTestUtils.setField(webhookWorker, "maxAttempts", 2);
        ReflectionTestUtils.setField(webhookWorker, "batchSize", 10);
        ReflectionTestUtils.setField(webhookWorker, "processingInterval", 10000L);
    }

    @Nested
    @DisplayName("processSubscriptionUpdateJob")
    class ProcessSubscriptionUpdateJobTests {
        @Test
        @DisplayName("Should short-circuit when idempotency exists")
        void shouldShortCircuitWhenIdempotencyExists() throws Exception {
            String payload = "{\"subscription\":\"sub-1\"}";
            String signature = "sig-123";

            when(webhookIdempotencyRepository.findBySignature(signature))
                    .thenReturn(Optional.of(new WebhookIdempotency()));

            webhookWorker.processSubscriptionUpdateJob(payload, signature);

            verify(webhookIdempotencyRepository).updateLastProcessedAt(eq(signature), any(Instant.class));
            verifyNoInteractions(webhookJobRepository);
        }

        @Test
        @DisplayName("Should create job and idempotency when first time")
        void shouldCreateJobAndIdempotency() throws Exception {
            String payload = "{\"subscription\":\"sub-42\"}";
            String signature = "sig-xyz";

            when(webhookIdempotencyRepository.findBySignature(signature))
                    .thenReturn(Optional.empty());

            // Return JsonNode with subscription id
            JsonNode node = new ObjectMapper().readTree(payload);
            when(objectMapper.readTree(payload)).thenReturn(node);

            when(webhookJobRepository.save(any(WebhookJob.class))).thenAnswer(inv -> {
                WebhookJob j = inv.getArgument(0);
                j.setId(777L);
                return j;
            });

            ArgumentCaptor<WebhookIdempotency> idemCaptor = ArgumentCaptor.forClass(WebhookIdempotency.class);

            webhookWorker.processSubscriptionUpdateJob(payload, signature);

            verify(webhookJobRepository).save(any(WebhookJob.class));
            verify(webhookIdempotencyRepository).save(idemCaptor.capture());

            WebhookIdempotency saved = idemCaptor.getValue();
            assertEquals("sub-42", saved.getSubscriptionId());
            assertEquals(777L, saved.getWebhookJobId());
        }
    }

    @Nested
    @DisplayName("processPendingJobs")
    class ProcessPendingJobsTests {
        @Test
        @DisplayName("Should return early when there are no pending jobs")
        void shouldReturnEarlyWhenNoPendingJobs() {
            when(webhookJobService.getPendingJobsReadyForExecution()).thenReturn(Collections.emptyList());

            webhookWorker.processPendingJobs();

            verify(webhookJobService, never()).updateJobStatus(anyLong(), any());
        }
    }

    @Nested
    @DisplayName("processJob")
    class ProcessJobTests {
        @Test
        @DisplayName("Should set RUNNING then DONE for subscription_update with missing subscription")
        void shouldSetRunningThenDone() throws Exception {
            // Include required fields to avoid JSON null pointer in service logic
            WebhookJob job = makeJob(10L, JobStatus.PENDING, "{\"subscription\":\"sub-1\",\"status\":1,\"number_recurrences\":2}");

            JsonNode node = new ObjectMapper().readTree(job.getPayload());
            when(objectMapper.readTree(job.getPayload())).thenReturn(node);
            when(subscriptionRepository.findById("sub-1")).thenReturn(Optional.empty());

            webhookWorker.processJob(job);

            verify(webhookJobService).updateJobStatus(10L, JobStatus.RUNNING);
            verify(webhookJobService).updateJobStatus(10L, JobStatus.DONE);
            verifyNoInteractions(shopifySyncOperationRepository);
        }

        @Test
        @DisplayName("Should schedule retry and set PENDING on failure below max attempts")
        void shouldRetryAndSetPendingOnFailure() throws Exception {
            WebhookJob job = makeJob(11L, JobStatus.PENDING, "invalid-json");
            job.setAttempts(0); // newAttempts = 1 (< max=2)

            when(objectMapper.readTree(job.getPayload())).thenThrow(new RuntimeException("boom"));

            webhookWorker.processJob(job);

            verify(webhookJobService).updateJobStatus(11L, JobStatus.RUNNING);
            verify(webhookJobService).incrementAttemptsAndScheduleNext(eq(11L), any(Instant.class));
            verify(webhookJobService).updateJobStatus(11L, JobStatus.PENDING);
        }

        @Test
        @DisplayName("Should mark DEAD after reaching max attempts")
        void shouldMarkDeadAfterMaxAttempts() throws Exception {
            WebhookJob job = makeJob(12L, JobStatus.PENDING, "invalid-json");
            job.setAttempts(1); // newAttempts = 2 (>= max=2)

            when(objectMapper.readTree(job.getPayload())).thenThrow(new RuntimeException("boom"));

            webhookWorker.processJob(job);

            verify(webhookJobService).updateJobStatus(12L, JobStatus.RUNNING);
            verify(webhookJobService).updateJobStatus(12L, JobStatus.DEAD);
            verify(webhookJobService, never()).incrementAttemptsAndScheduleNext(anyLong(), any());
        }
    }

    private WebhookJob makeJob(Long id, JobStatus status, String payload) {
        WebhookJob j = new WebhookJob();
        j.setId(id);
        j.setSource("pagstream");
        j.setEventType("subscription_update");
        j.setSubscriptionId("sub-1");
        j.setPayload(payload);
        j.setStatus(status);
        j.setAttempts(0);
        j.setNextRunAt(Instant.now());
        j.setCreatedAt(Instant.now());
        j.setUpdatedAt(Instant.now());
        return j;
    }
}
