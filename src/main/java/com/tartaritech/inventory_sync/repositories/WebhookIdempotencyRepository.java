package com.tartaritech.inventory_sync.repositories;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.tartaritech.inventory_sync.entities.WebhookIdempotency;

@Repository
public interface WebhookIdempotencyRepository extends JpaRepository<WebhookIdempotency, Long> {
    
    /**
     * Busca um registro de idempotência pelo secret
     */
    Optional<WebhookIdempotency> findBySignature(String signature);
    
    /**
     * Verifica se existe um registro de idempotência para o secret
     */
    boolean existsBySignature(String signature);
    
    /**
     * Remove registros antigos baseado no TTL (Time To Live)
     * Útil para limpeza automática de registros antigos
     */
    @Modifying
    @Query("DELETE FROM WebhookIdempotency w WHERE w.createdAt < :cutoffTime")
    int deleteByCreatedAtBefore(@Param("cutoffTime") Instant cutoffTime);
    
    /**
     * Busca registros por source e eventType para análise
     */
    @Query("SELECT w FROM WebhookIdempotency w WHERE w.source = :source AND w.eventType = :eventType")
    java.util.List<WebhookIdempotency> findBySourceAndEventType(@Param("source") String source, @Param("eventType") String eventType);
    
    /**
     * Atualiza o lastProcessedAt de um registro existente
     */
    @Modifying
    @Query("UPDATE WebhookIdempotency w SET w.lastProcessedAt = :lastProcessedAt WHERE w.signature = :signature")
    int updateLastProcessedAt(@Param("signature") String signature, @Param("lastProcessedAt") Instant lastProcessedAt);
    
    /**
     * Remove registros antigos onde o job foi processado com sucesso (DONE)
     * Usado para limpeza conservadora
     */
    @Modifying
    @Query("DELETE FROM WebhookIdempotency w WHERE w.createdAt < :cutoffTime AND w.webhookJobId IN " +
           "(SELECT j.id FROM WebhookJob j WHERE j.status = :status)")
    int deleteByCreatedAtBeforeAndJobStatus(@Param("cutoffTime") Instant cutoffTime, @Param("status") com.tartaritech.inventory_sync.enums.JobStatus status);
}
