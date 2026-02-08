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
public class RecurrenceDTO {

    private String order;

    @JsonProperty("number_recurrence")
    private Integer numberRecurrence;

    private Integer skipped;

    @JsonProperty("payment_method")
    private String paymentMethod;

    @JsonProperty("order_status")
    private String orderStatus;

    private String link;

    @JsonProperty("amount_brl")
    private String amountBrl;

    @JsonProperty("amount_original")
    private String amountOriginal;

    @JsonProperty("payment_date")
    private String paymentDate;

    @JsonProperty("customer_email")
    private String customerEmail;

    private List<ProductDTO> products;
}
