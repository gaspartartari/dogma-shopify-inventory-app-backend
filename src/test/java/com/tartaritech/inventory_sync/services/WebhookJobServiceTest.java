package com.tartaritech.inventory_sync.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.tartaritech.inventory_sync.dtos.JobStatsDTO;
import com.tartaritech.inventory_sync.entities.WebhookJob;
import com.tartaritech.inventory_sync.enums.JobStatus;
import com.tartaritech.inventory_sync.repositories.WebhookJobRepository;

@ExtendWith(SpringExtension.class)
public class WebhookJobServiceTest {

    @InjectMocks
    private WebhookJobService webhookJobService;

    @Mock
    private WebhookJobRepository webhookJobRepository;

    private WebhookJob job1Pending, job2Running, job3FailedRecent, job4FailedOld, job5RunningOld, job6Done, job7SourceEvt;

    @BeforeEach
    void setup() {
        Instant now = Instant.now();

        job1Pending = buildJob(1L, JobStatus.PENDING, "pagstream", "subscription_update", "sub-1", now.minusSeconds(300));
        job2Running = buildJob(2L, JobStatus.RUNNING, "pagstream", "subscription_update", "sub-2", now.minusSeconds(1200));
        job3FailedRecent = buildJob(3L, JobStatus.FAILED, "pagstream", "product_update", "sub-3", now.minusSeconds(1200 / 2)); // 10 min
        job4FailedOld = buildJob(4L, JobStatus.FAILED, "other", "other_evt", "sub-4", now.minusSeconds(7200)); // 2 hours
        job5RunningOld = buildJob(5L, JobStatus.RUNNING, "pagstream", "subscription_update", "sub-5", now.minusSeconds(7200));
        job6Done = buildJob(6L, JobStatus.DONE, "pagstream", "subscription_update", "sub-6", now.minusSeconds(60));
        job7SourceEvt = buildJob(7L, JobStatus.PENDING, "my-source", "evt-x", "sub-7", now.minusSeconds(60));
    }

    @Test
    public void getPendingJobsReadyForExecutionShouldDelegateToRepository() {
        List<WebhookJob> expected = List.of(job1Pending, job7SourceEvt);
        when(webhookJobRepository.findPendingJobsReadyForExecution(eq(JobStatus.PENDING), any(Instant.class)))
                .thenReturn(expected);

        List<WebhookJob> result = webhookJobService.getPendingJobsReadyForExecution();

        assertEquals(expected, result);
        verify(webhookJobRepository).findPendingJobsReadyForExecution(eq(JobStatus.PENDING), any(Instant.class));
    }

    @Test
    public void updateJobStatusShouldInvokeRepositoryWithNow() {
        when(webhookJobRepository.updateJobStatus(eq(10L), eq(JobStatus.DONE), any(Instant.class))).thenReturn(1);

        webhookJobService.updateJobStatus(10L, JobStatus.DONE);

        verify(webhookJobRepository).updateJobStatus(eq(10L), eq(JobStatus.DONE), any(Instant.class));
    }

    @Test
    public void incrementAttemptsAndScheduleNextShouldInvokeRepositoryWithNextRun() {
        Instant nextRunAt = Instant.now().plusSeconds(600);
        when(webhookJobRepository.incrementAttemptsAndScheduleNext(eq(11L), eq(nextRunAt), any(Instant.class)))
                .thenReturn(1);

        webhookJobService.incrementAttemptsAndScheduleNext(11L, nextRunAt);

        verify(webhookJobRepository).incrementAttemptsAndScheduleNext(eq(11L), eq(nextRunAt), any(Instant.class));
    }

    @Test
    public void getJobStatsShouldAggregateCounts() {
        when(webhookJobRepository.countByStatus(JobStatus.PENDING)).thenReturn(3L);
        when(webhookJobRepository.countByStatus(JobStatus.RUNNING)).thenReturn(2L);
        when(webhookJobRepository.countByStatus(JobStatus.DONE)).thenReturn(5L);
        when(webhookJobRepository.countByStatus(JobStatus.FAILED)).thenReturn(1L);
        when(webhookJobRepository.countByStatus(JobStatus.DEAD)).thenReturn(4L);

        JobStatsDTO stats = webhookJobService.getJobStats();

        assertNotNull(stats);
        assertEquals(3L, stats.getPending());
        assertEquals(2L, stats.getRunning());
        assertEquals(5L, stats.getDone());
        assertEquals(1L, stats.getFailed());
        assertEquals(4L, stats.getDead());
        assertEquals(3L + 2L + 5L + 1L + 4L, stats.getTotal());
    }

    @Test
    public void getJobsWithFiltersShouldFilterBySourceEventAndStatus() {
        when(webhookJobRepository.findAll()).thenReturn(Arrays.asList(
                job1Pending, job2Running, job3FailedRecent, job4FailedOld, job5RunningOld, job6Done, job7SourceEvt));

        List<WebhookJob> result = webhookJobService.getJobsWithFilters("my-source", "evt-x", JobStatus.PENDING);

        assertEquals(1, result.size());
        assertEquals("my-source", result.get(0).getSource());
        assertEquals("evt-x", result.get(0).getEventType());
        assertEquals(JobStatus.PENDING, result.get(0).getStatus());
    }

    @Test
    public void getRecentFailedJobsShouldReturnFailedWithinLastHour() {
        when(webhookJobRepository.findAll()).thenReturn(Arrays.asList(
                job1Pending, job2Running, job3FailedRecent, job4FailedOld, job6Done));

        List<WebhookJob> result = webhookJobService.getRecentFailedJobs();

        assertEquals(1, result.size());
        assertEquals(JobStatus.FAILED, result.get(0).getStatus());
        assertTrue(result.get(0).getUpdatedAt().isAfter(Instant.now().minusSeconds(3600)));
    }

    @Test
    public void getStuckRunningJobsShouldReturnRunningOlderThanOneHour() {
        when(webhookJobRepository.findAll()).thenReturn(Arrays.asList(
                job2Running, job5RunningOld));

        List<WebhookJob> result = webhookJobService.getStuckRunningJobs();

        assertEquals(1, result.size());
        assertEquals(JobStatus.RUNNING, result.get(0).getStatus());
        assertTrue(result.get(0).getUpdatedAt().isBefore(Instant.now().minusSeconds(3600)));
    }

    private WebhookJob buildJob(Long id, JobStatus status, String source, String eventType,
                                String subscriptionId, Instant updatedAt) {
        WebhookJob job = new WebhookJob();
        job.setId(id);
        job.setStatus(status);
        job.setSource(source);
        job.setEventType(eventType);
        job.setSubscriptionId(subscriptionId);
        job.setUpdatedAt(updatedAt);
        job.setCreatedAt(updatedAt.minusSeconds(120));
        job.setAttempts(0);
        job.setNextRunAt(updatedAt.plusSeconds(60));
        job.setPayload("{}");
        return job;
    }
}
