package com.tartaritech.inventory_sync.entities;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
@Table(name = "tb_webhook_idempotency", indexes = {
    @Index(name = "idx_webhook_idempotency_signature", columnList = "signature", unique = true),
    @Index(name = "idx_webhook_idempotency_created", columnList = "created_at")
})
public class WebhookIdempotency {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String signature; // Secret HMAC usado como chave única

    @Column(nullable = false)
    private String source; // "pagstream"

    @Column(nullable = false)
    private String eventType; // "subscription_update"

    @Column(nullable = false)
    private String subscriptionId;

    @Column(nullable = false)
    private Long webhookJobId; // Referência ao job processado

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant lastProcessedAt;

    /**
     * Factory method para criar um novo WebhookIdempotency
     */
    public static WebhookIdempotency createNew(String signature, String source, String eventType, 
                                             String subscriptionId, Long webhookJobId) {
        WebhookIdempotency idempotency = new WebhookIdempotency();
        idempotency.setSignature(signature);
        idempotency.setSource(source);
        idempotency.setEventType(eventType);
        idempotency.setSubscriptionId(subscriptionId);
        idempotency.setWebhookJobId(webhookJobId);
        
        return idempotency;
    }

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        lastProcessedAt = now;
    }

    
}
