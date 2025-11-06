package com.tartaritech.inventory_sync.dtos;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.text.DateFormatter;

import com.tartaritech.inventory_sync.entities.Order;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class OrderWithDetailsDTO {
    
    private Long orderId;
    private String orderNumber;
    private int numberRecurrence;
    private String paymentDate;
    private String updatedDate;
    
    // Customer info
    private String customerName;
    private String customerEmail;
    private String customerPhone;
    
    // Subscription info
    private String subscriptionId;
    private String subscriptionStatus;
    private Integer subscriptionTotalRecurrences;
    private String subscriptionNextBillingDate;
    
    // Products
    private List<ProductDetailDTO> products;
    
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter formatterDateOnly = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    
    public OrderWithDetailsDTO(Order entity) {
        this.orderId = entity.getId();
        this.orderNumber = entity.getOrderRec();
        this.numberRecurrence = entity.getNumberRecurrence();
        this.paymentDate = entity.getPaymentDate();
        this.updatedDate = entity.getLastModifiedDate() != null 
            ? entity.getLastModifiedDate().format(formatter) 
            : null;
        
        // Customer info
        if (entity.getSubscription() != null && entity.getSubscription().getCustomer() != null) {
            this.customerName = entity.getSubscription().getCustomer().getName();
            this.customerEmail = entity.getSubscription().getCustomer().getEmail();
            this.customerPhone = entity.getSubscription().getCustomer().getPhone();
        }
        
        // Subscription info
        if (entity.getSubscription() != null) {
            this.subscriptionId = entity.getSubscription().getId();
            this.subscriptionStatus = entity.getSubscription().getStatus() != null 
                ? entity.getSubscription().getStatus().name() 
                : null;
            this.subscriptionTotalRecurrences = entity.getSubscription().getNumberRecurrences();
            this.subscriptionNextBillingDate = entity.getSubscription().getNextBillingDate().format(formatterDateOnly);
        }
        
        // Products
        this.products = entity.getProducts() != null && !entity.getProducts().isEmpty()
            ? entity.getProducts().stream()
                .map(ProductDetailDTO::new)
                .collect(Collectors.toList())
            : List.of();
    }
}

