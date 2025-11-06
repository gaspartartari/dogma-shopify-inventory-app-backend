package com.tartaritech.inventory_sync.dtos;

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
public class SkuSummaryDTO {
    
    private String sku;
    private String skuName;
    private Long totalQuantity;
    private Double skuOrderRevenue;
    private Long orderCount;

    
}

