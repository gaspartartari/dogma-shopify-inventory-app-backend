package com.tartaritech.inventory_sync.dtos;

import java.util.List;
import java.util.stream.Collectors;

import com.tartaritech.inventory_sync.entities.Customer;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class CustomerDTO {
   
    private String email;
    private String name;
    private String phone;
    private List<SubscriptionDTO> subscriptions;

    public CustomerDTO (Customer entity) {
    
        this.email = entity.getEmail() != null ? entity.getEmail() : null;
        this.name = entity.getName() != null ? entity.getName() : null;
        this.phone = entity.getPhone() != null ? entity.getPhone() : null;

        this.subscriptions = !entity.getSubscriptions().isEmpty() ?
            entity.getSubscriptions()
                .stream().map(s -> new SubscriptionDTO(s))
                    .collect(Collectors.toList()) : null;
    }
}
