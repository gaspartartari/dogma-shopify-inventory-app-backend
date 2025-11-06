package com.tartaritech.inventory_sync.dtos;

import java.math.BigDecimal;

import com.tartaritech.inventory_sync.entities.Product;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class ProductDetailDTO {
    
    private Long productId;
    private String sku;
    private String skuName;
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalPrice;
    
    public ProductDetailDTO(Product entity) {
        this.productId = entity.getId();
        if (entity.getControlledSku() != null) {
            this.sku = entity.getControlledSku().getSku();
            this.skuName = entity.getControlledSku().getName();
        }
        this.quantity = entity.getQuantity();
        this.unitPrice = entity.getUnitPrice();
        this.totalPrice = entity.getTotalPrice();
    }
}

