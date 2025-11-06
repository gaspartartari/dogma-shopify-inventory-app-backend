package com.tartaritech.inventory_sync.dtos;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tartaritech.inventory_sync.entities.Subscription;

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
public class SubscriptionDTO {

        @JsonProperty("subscription")
        private String id;

        private int status;

        @JsonProperty("number_recurrences")
        private Integer numberRecurrences;

        private int nextRecurrence;

        @JsonProperty("next_billing_date")
        private LocalDate nextBillingDate;

        @JsonProperty("cancellation_date")
        private LocalDate cancellationDate;

        @JsonProperty("customer_name")
        private String customerName;

        @JsonProperty("customer_email")
        private String customerEmail;

        @JsonProperty("customer_phone")
        private String customerPhone;

        @JsonProperty("recurrences")
        private Set<OrderDTO> orders = new HashSet<>();


        public void processOrders() {
                if (this.numberRecurrences != null) {
                        this.orders = this.orders.stream()
                                        .filter(r -> r.getOrder() == null
                                                        || r.getNumberRecurrence() == this.numberRecurrences)
                                        .collect(Collectors.toSet());
                }
        }

        public SubscriptionDTO(Subscription entity) {
                this.id = entity.getId();
                this.status = entity.getStatus() != null ? entity.getStatus().getSubscriptionStatus() : null;
                this.numberRecurrences = entity.getNumberRecurrences();
                this.nextBillingDate = entity.getNextBillingDate() != null ? entity.getNextBillingDate() : null;
                this.cancellationDate = entity.getCancellationDate() != null ? entity.getCancellationDate() : null;
                this.nextRecurrence = entity.getNextRecurrence();

                this.orders = !entity.getRecurrences().isEmpty() ? entity.getRecurrences().stream().map(OrderDTO::new).collect(Collectors.toSet()) : null;

                this.customerName = entity.getCustomer() != null
                                ? entity.getCustomer().getName() != null ? entity.getCustomer().getName() : null
                                : null;

                this.customerEmail = entity.getCustomer() != null
                                ? entity.getCustomer().getEmail() != null ? entity.getCustomer().getEmail() : null
                                : null;

                this.customerPhone = entity.getCustomer() != null
                                ? entity.getCustomer().getPhone() != null ? entity.getCustomer().getPhone() : null
                                : null;

        }

}
