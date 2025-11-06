package com.tartaritech.inventory_sync.dtos;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
public class RevenueDataPointDTO {
    
    private String yearMonth; // Format: "YYYY-MM"
    private BigDecimal totalRevenue;
    
    public RevenueDataPointDTO(String yearMonth, Double totalRevenue) {
        this.yearMonth = yearMonth;
        this.totalRevenue = totalRevenue != null ? BigDecimal.valueOf(totalRevenue) : BigDecimal.ZERO;
    }
}

