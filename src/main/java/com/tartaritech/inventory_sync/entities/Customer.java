package com.tartaritech.inventory_sync.entities;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
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
@Table(name = "tb_customer")
public class Customer {

    @Id
    private String email;
    private String name;
    private String phone;

    @OneToMany(mappedBy = "customer")
    private List<Subscription> subscriptions = new ArrayList<>();

}
