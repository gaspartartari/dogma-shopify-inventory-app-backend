package com.tartaritech.inventory_sync.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tartaritech.inventory_sync.entities.Customer;

public interface CustomerRepository extends JpaRepository <Customer, String> {
    
}
