package com.tartaritech.inventory_sync.repositories;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.tartaritech.inventory_sync.entities.WebhookJob;
import com.tartaritech.inventory_sync.enums.JobStatus;

public interface WebhookJobRepository extends JpaRepository<WebhookJob, Long> {
    
    /**
     * Busca jobs pendentes que estão prontos para execução
     */
    @Query("SELECT j FROM WebhookJob j WHERE j.status = :status AND j.nextRunAt <= :now ORDER BY j.nextRunAt ASC")
    List<WebhookJob> findPendingJobsReadyForExecution(@Param("status") JobStatus status, @Param("now") Instant now);
    
    /**
     * Busca jobs pendentes com limite
     */
    @Query("SELECT j FROM WebhookJob j WHERE j.status = :status AND j.nextRunAt <= :now ORDER BY j.nextRunAt ASC")
    List<WebhookJob> findPendingJobsReadyForExecutionWithLimit(@Param("status") JobStatus status, @Param("now") Instant now, org.springframework.data.domain.Pageable pageable);
    
    /**
     * Atualiza status do job
     */
    @Modifying
    @Query("UPDATE WebhookJob j SET j.status = :status, j.updatedAt = :updatedAt WHERE j.id = :id")
    int updateJobStatus(@Param("id") Long id, @Param("status") JobStatus status, @Param("updatedAt") Instant updatedAt);
    
    /**
     * Incrementa tentativas e agenda próxima execução
     */
    @Modifying
    @Query("UPDATE WebhookJob j SET j.attempts = j.attempts + 1, j.nextRunAt = :nextRunAt, j.updatedAt = :updatedAt WHERE j.id = :id")
    int incrementAttemptsAndScheduleNext(@Param("id") Long id, @Param("nextRunAt") Instant nextRunAt, @Param("updatedAt") Instant updatedAt);
    
    /**
     * Conta jobs por status
     */
    long countByStatus(JobStatus status);
}
