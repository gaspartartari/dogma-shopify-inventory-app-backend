package com.tartaritech.inventory_sync.dtos;

import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tartaritech.inventory_sync.entities.Order;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderDTO {


    private Long id;

    private String order;

    @JsonProperty(value = "number_recurrence")
    private int numberRecurrence;

    @JsonProperty(value = "payment_date")
    private String paymentDate;
    
    private List<ProductDTO> products;
    
    private String subscription;

    public OrderDTO(Order entity) {
        this.id = entity.getId();
        this.order = entity.getOrderRec();
        this.numberRecurrence = entity.getNumberRecurrence();
        this.paymentDate = entity.getPaymentDate();
        this.products = !entity.getProducts().isEmpty() ?
            entity.getProducts().stream().map(ProductDTO::new).collect(Collectors.toList()) : null;
        this.subscription = entity.getSubscription().getId();
    }
    
}
