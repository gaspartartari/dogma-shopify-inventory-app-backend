package com.tartaritech.inventory_sync.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tartaritech.inventory_sync.entities.Product;

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
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductDTO {
    
    private Long id;
    private String sku;
    private int quantity;
    private String order;
    
    @JsonProperty("unit_price")
    private String unitPrice;
    
    @JsonProperty("amount_total")
    private String totalPrice;
    
    public ProductDTO(Product entity) {
        this.id = entity.getId();
        this.sku = entity.getControlledSku().getSku();
        this.quantity = entity.getQuantity();
        this.order = entity.getOrder().getOrderRec();
        this.unitPrice = entity.getUnitPrice() != null ? entity.getUnitPrice().toString() : null;
        this.totalPrice = entity.getTotalPrice() != null ? entity.getTotalPrice().toString() : null;
    }
}
