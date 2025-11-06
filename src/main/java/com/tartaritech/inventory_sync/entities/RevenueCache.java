package com.tartaritech.inventory_sync.entities;

import java.math.BigDecimal;
import java.time.Instant;

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
@Table(name = "tb_revenue_cache", indexes = {
    @Index(name = "idx_year_month", columnList = "year_month")
})
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class RevenueCache {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "year_month", nullable = false, unique = true, length = 7)
    private String yearMonth;  // Format: "YYYY-MM"
    
    @Column(name = "total_revenue", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalRevenue;
    
    @Column(name = "last_updated_at", nullable = false)
    private Instant lastUpdatedAt;
    
    @PrePersist
    @PreUpdate
    void updateTimestamp() {
        lastUpdatedAt = Instant.now();
    }
    
    public static RevenueCache create(String yearMonth, BigDecimal totalRevenue) {
        RevenueCache cache = new RevenueCache();
        cache.setYearMonth(yearMonth);
        cache.setTotalRevenue(totalRevenue);
        cache.setLastUpdatedAt(Instant.now());
        return cache;
    }
}

