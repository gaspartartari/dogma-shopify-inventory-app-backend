package com.tartaritech.inventory_sync.dtos;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class SubscriptionsDTO {
    
    List<SubscriptionShortDTO> subscriptions = new ArrayList<>();
}
