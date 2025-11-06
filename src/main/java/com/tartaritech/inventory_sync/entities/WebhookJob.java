package com.tartaritech.inventory_sync.entities;

import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;
import com.tartaritech.inventory_sync.enums.JobStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Table(name = "tb_webhook_job", indexes = {
    @Index(name = "idx_webhook_job_next", columnList = "next_run_at")
})

public class WebhookJob {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String source; // "pagstream"

    @Column(nullable = false)
    private String eventType; // "subscription_update"

    @Column(nullable = false)
    private String subscriptionId;

    @Column(columnDefinition = "CLOB", nullable = false)
    private String payload;

    @Column(nullable = false)
    private JobStatus status; // PENDING/RUNNING/DONE/FAILED/DEAD

    @Column(nullable = false)
    private Integer attempts;

    @Column(nullable = false)
    private Instant nextRunAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;


    /**
     * Factory method para criar um novo WebhookJob
     */
    public static WebhookJob createNewJob(String source, String eventType, String payload, String subscriptionId) {
        WebhookJob job = new WebhookJob();
        job.setSource(source);
        job.setEventType(eventType);
        job.setPayload(payload);
        job.setSubscriptionId(subscriptionId);
        job.setNextRunAt(Instant.now());
        job.setAttempts(0);
        job.setStatus(JobStatus.PENDING);
        
        return job;
    }


    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        WebhookJob other = (WebhookJob) obj;
        if (id == null) {
            if (other.id != null)
                return false;
        } else if (!id.equals(other.id))
            return false;
        return true;
    }

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (attempts == null)
            attempts = 0;
        if (status == null)
            status = JobStatus.PENDING;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }


    
  
}