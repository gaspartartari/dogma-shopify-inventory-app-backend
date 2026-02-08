package com.tartaritech.inventory_sync.dtos;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class SubscriptionFullDTO {

    @JsonProperty("subscription")
    private String subscription;

    private Integer status;

    @JsonProperty("billing_cycle")
    private String billingCycle;

    @JsonProperty("shipping_cycle")
    private String shippingCycle;

    @JsonProperty("amount_brl")
    private String amountBrl;

    @JsonProperty("number_recurrences")
    private Integer numberRecurrences;

    private Integer limit;

    @JsonProperty("next_billing_date")
    private String nextBillingDate;

    @JsonProperty("cancellation_date")
    private String cancellationDate;

    @JsonProperty("effective_cancellation_date")
    private String effectiveCancellationDate;

    @JsonProperty("order_token")
    private String orderToken;

    @JsonProperty("pix_rec_id")
    private String pixRecId;

    @JsonProperty("customer_email")
    private String customerEmail;

    @JsonProperty("customer_name")
    private String customerName;

    @JsonProperty("customer_phone")
    private String customerPhone;

    private List<RecurrenceDTO> recurrences;

    private List<ProductDTO> products;
}
