package com.tartaritech.inventory_sync.entities;

import java.math.BigDecimal;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@ToString
@Table(name = "tb_product")
public class Product{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "sku")
    private ControlledSKu controlledSku;

    private Integer quantity;

    private BigDecimal unitPrice;

    private BigDecimal totalPrice;
    
    private String discount;
    
    private String category;

    @ManyToOne
    @JoinColumn(name = "order_id")
    private Order order;

    public static Product createProduct(ControlledSKu sku, int quantity, BigDecimal unitPrice, BigDecimal totalPrice) {
        Product product = new Product();
        product.setControlledSku(sku);
        product.setQuantity(quantity);
        product.setUnitPrice(unitPrice);
        product.setTotalPrice(totalPrice);
        return product;
        
    }

}
