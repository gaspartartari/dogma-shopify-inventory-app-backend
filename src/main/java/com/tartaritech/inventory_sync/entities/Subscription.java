package com.tartaritech.inventory_sync.entities;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.tartaritech.inventory_sync.enums.SubscriptionStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
@Entity
@Table(name = "tb_subscription")
public class Subscription {

    @Id
    private String id;

    @Enumerated(EnumType.STRING)
    private SubscriptionStatus status;

    private int numberRecurrences;
    private int nextRecurrence;
    private LocalDate nextBillingDate;
    private LocalDate cancellationDate;

    @OneToMany(mappedBy = "subscription")
    private List<Order> recurrences = new ArrayList<>();
    

    @ManyToOne
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;



    public static SubscriptionStatus convertStatusToEnum(int value) {
        SubscriptionStatus result = null;
        switch (value) {
            case 0:
                result = SubscriptionStatus.AGUARDANDO_PRIMEIRO_PAGAMENTO;
                break;

            case 1:
                result = SubscriptionStatus.ATIVO;
                break;

            case 2:
                result = SubscriptionStatus.PAGAMENTO_PENDENTE;
                break;

            case 3:
                result = SubscriptionStatus.INATIVO_CANCELADO;
                break;

            case 4:
                result = SubscriptionStatus.EXPIRADO;
                break;

            case 5:
                result = SubscriptionStatus.PAUSADO;
                break;

            case 6:
                result = SubscriptionStatus.PAGAMENTO_ATRASADO;
                break;

            default:
                break;
        }

        return result;
    }


    public static Integer convertEnumStatustoInteger(SubscriptionStatus statusEnum) {
        Integer result = null;
        switch (statusEnum) {
            case AGUARDANDO_PRIMEIRO_PAGAMENTO:
                result = 0;
                break;

            case ATIVO:
                result = 1;
                break;

            case PAGAMENTO_PENDENTE:
                result = 2;
                break;

            case INATIVO_CANCELADO:
                result = 3;
                break;

            case EXPIRADO:
                result = 4;
                break;

            case PAUSADO:
                result = 5;
                break;

            case PAGAMENTO_ATRASADO:
                result = 6;
                break;

            default:
                break;
        }

        return result;
    }

    @PrePersist
    void prePersist() {
        var now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

}
