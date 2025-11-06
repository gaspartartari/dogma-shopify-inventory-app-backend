package com.tartaritech.inventory_sync.services;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tartaritech.inventory_sync.dtos.JobStatsDTO;
import com.tartaritech.inventory_sync.entities.WebhookJob;
import com.tartaritech.inventory_sync.enums.JobStatus;
import com.tartaritech.inventory_sync.repositories.WebhookJobRepository;

@Service
public class WebhookJobService {

    private final WebhookJobRepository webhookJobRepository;

    public WebhookJobService(WebhookJobRepository webhookJobRepository) {
        this.webhookJobRepository = webhookJobRepository;
    }

    /**
     * Busca todos os jobs
     */
    public List<WebhookJob> getAllJobs() {
        return webhookJobRepository.findAll();
    }

    /**
     * Busca job por ID
     */
    public Optional<WebhookJob> getJobById(Long id) {
        return webhookJobRepository.findById(id);
    }

    /**
     * Busca jobs por status
     */
    public List<WebhookJob> getJobsByStatus(JobStatus status) {
        return webhookJobRepository.findAll().stream()
                .filter(job -> job.getStatus() == status)
                .toList();
    }

    /**
     * Busca jobs pendentes prontos para execução
     */
    public List<WebhookJob> getPendingJobsReadyForExecution() {
        Instant now = Instant.now();
        return webhookJobRepository.findPendingJobsReadyForExecution(JobStatus.PENDING, now);
    }

    /**
     * Gera estatísticas dos jobs
     */
    public JobStatsDTO getJobStats() {
        long pendingCount = webhookJobRepository.countByStatus(JobStatus.PENDING);
        long runningCount = webhookJobRepository.countByStatus(JobStatus.RUNNING);
        long doneCount = webhookJobRepository.countByStatus(JobStatus.DONE);
        long failedCount = webhookJobRepository.countByStatus(JobStatus.FAILED);
        long deadCount = webhookJobRepository.countByStatus(JobStatus.DEAD);

        JobStatsDTO stats = new JobStatsDTO();
        stats.setPending(pendingCount);
        stats.setRunning(runningCount);
        stats.setDone(doneCount);
        stats.setFailed(failedCount);
        stats.setDead(deadCount);
        stats.setTotal(pendingCount + runningCount + doneCount + failedCount + deadCount);

        return stats;
    }

    /**
     * Busca jobs por source
     */
    public List<WebhookJob> getJobsBySource(String source) {
        return webhookJobRepository.findAll().stream()
                .filter(job -> source.equals(job.getSource()))
                .toList();
    }

    /**
     * Busca jobs por eventType
     */
    public List<WebhookJob> getJobsByEventType(String eventType) {
        return webhookJobRepository.findAll().stream()
                .filter(job -> eventType.equals(job.getEventType()))
                .toList();
    }

    /**
     * Busca jobs por subscriptionId
     */
    public List<WebhookJob> getJobsBySubscriptionId(String subscriptionId) {
        return webhookJobRepository.findAll().stream()
                .filter(job -> subscriptionId.equals(job.getSubscriptionId()))
                .toList();
    }

    /**
     * Busca jobs com filtros combinados
     */
    public List<WebhookJob> getJobsWithFilters(String source, String eventType, JobStatus status) {
        return webhookJobRepository.findAll().stream()
                .filter(job -> source == null || source.equals(job.getSource()))
                .filter(job -> eventType == null || eventType.equals(job.getEventType()))
                .filter(job -> status == null || status == job.getStatus())
                .toList();
    }

    /**
     * Atualiza status do job
     */
    @Transactional
    public void updateJobStatus(Long jobId, JobStatus status) {
        Instant now = Instant.now();
        webhookJobRepository.updateJobStatus(jobId, status, now);
    }

    /**
     * Incrementa tentativas e agenda próxima execução
     */
    @Transactional
    public void incrementAttemptsAndScheduleNext(Long jobId, Instant nextRunAt) {
        Instant now = Instant.now();
        webhookJobRepository.incrementAttemptsAndScheduleNext(jobId, nextRunAt, now);
    }

    /**
     * Conta jobs por status
     */
    public long countJobsByStatus(JobStatus status) {
        return webhookJobRepository.countByStatus(status);
    }

    /**
     * Busca jobs que falharam recentemente
     */
    public List<WebhookJob> getRecentFailedJobs() {
        Instant oneHourAgo = Instant.now().minusSeconds(3600);
        return webhookJobRepository.findAll().stream()
                .filter(job -> job.getStatus() == JobStatus.FAILED)
                .filter(job -> job.getUpdatedAt().isAfter(oneHourAgo))
                .toList();
    }

    /**
     * Busca jobs que estão rodando há muito tempo (possível travamento)
     */
    public List<WebhookJob> getStuckRunningJobs() {
        Instant oneHourAgo = Instant.now().minusSeconds(3600);
        return webhookJobRepository.findAll().stream()
                .filter(job -> job.getStatus() == JobStatus.RUNNING)
                .filter(job -> job.getUpdatedAt().isBefore(oneHourAgo))
                .toList();
    }
}
