package com.tartaritech.inventory_sync.entities;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tb_order")
@EntityListeners(AuditingEntityListener.class)
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_rec")
    private String orderRec;
    
    @Column(name = "number_recurrence")
    private Integer numberRecurrence;
    
    private Integer skipped;
    
    @Column(name = "payment_method")
    private String paymentMethod;
    
    @Column(name = "order_status")
    private String orderStatus;
    
    private String link;
    
    @Column(name = "amount_brl")
    private String amountBrl;
    
    @Column(name = "amount_original")
    private String amountOriginal;
    
    @Column(name = "payment_date")
    private String paymentDate;
    
    @Column(name = "customer_email")
    private String customerEmail;

    @Column(name = "stock_released", nullable = false)
    private Boolean stockReleased = false;

    @CreatedDate
    private LocalDateTime createdDate;

    @LastModifiedDate
    private LocalDateTime lastModifiedDate;

    @OneToMany(mappedBy = "order")
    private List<Product> products = new ArrayList<>();

    @ManyToOne
    @JoinColumn(name = "subscription_id")
    private Subscription subscription;

}
