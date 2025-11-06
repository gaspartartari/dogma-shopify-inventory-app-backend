package com.tartaritech.inventory_sync.entities;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "tb_shopify_sync_operation")
public class ShopifySyncOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String subscriptionId;

    @Column(nullable = false)
    private String operation; // "insert", "delete", "delta"

    @Column(nullable = false)
    private String sku;

    @Column(nullable = false)
    private Integer quantity; // stores delta value

    @Column(nullable = false)
    private Instant createdAt;

    private Instant executedAt;

    @Column(nullable = false)
    private String status; // "PENDING", "EXECUTED", "FAILED"

    private String errorMessage;

    @Column(nullable = false)
    private Integer retryCount = 0;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (retryCount == null) {
            retryCount = 0;
        }
    }
}
