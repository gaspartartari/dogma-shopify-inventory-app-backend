package com.tartaritech.inventory_sync.services;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tartaritech.inventory_sync.repositories.WebhookIdempotencyRepository;
import com.tartaritech.inventory_sync.enums.JobStatus;

@Service
public class IdempotencyCleanupService {

    private final WebhookIdempotencyRepository webhookIdempotencyRepository;

    @Value("${webhook.idempotency.ttl.hours:720}")
    private int ttlHours;

    @Value("${webhook.idempotency.cleanup.strategy:conservative}")
    private String cleanupStrategy; // conservative, aggressive

    public IdempotencyCleanupService(WebhookIdempotencyRepository webhookIdempotencyRepository) {
        this.webhookIdempotencyRepository = webhookIdempotencyRepository;
    }

    /**
     * Executa limpeza automática dos registros de idempotência antigos
     * Executa a cada 6 horas por padrão
     */
    @Scheduled(fixedRate = 21600000) // 6 horas em millisegundos
    @Transactional
    public void cleanupExpiredIdempotencyRecords() {
        int deletedCount = 0;
        
        if ("aggressive".equals(cleanupStrategy)) {
            // Limpeza agressiva: remove registros antigos independente do status
            deletedCount = cleanupAggressive();
        } else {
            // Limpeza conservadora: só remove se job foi processado com sucesso
            deletedCount = cleanupConservative();
        }
        
        if (deletedCount > 0) {
            System.out.println("Limpeza de idempotência (" + cleanupStrategy + "): " + deletedCount + " registros removidos");
        }
    }
    
    /**
     * Limpeza conservadora: só remove registros onde o job foi processado com sucesso
     */
    @Transactional
    public int cleanupConservative() {
        // Busca registros antigos onde o job foi processado com sucesso (DONE)
        return webhookIdempotencyRepository.deleteByCreatedAtBeforeAndJobStatus(
            Instant.now().minus(ttlHours, ChronoUnit.HOURS), 
            JobStatus.DONE
        );
    }
    
    /**
     * Limpeza agressiva: remove registros antigos independente do status
     */
    @Transactional
    public int cleanupAggressive() {
        Instant cutoffTime = Instant.now().minus(ttlHours, ChronoUnit.HOURS);
        return webhookIdempotencyRepository.deleteByCreatedAtBefore(cutoffTime);
    }

    /**
     * Executa limpeza manual dos registros de idempotência antigos
     */
    @Transactional
    public int cleanupExpiredIdempotencyRecordsManual() {
        Instant cutoffTime = Instant.now().minus(ttlHours, ChronoUnit.HOURS);
        return webhookIdempotencyRepository.deleteByCreatedAtBefore(cutoffTime);
    }
}
