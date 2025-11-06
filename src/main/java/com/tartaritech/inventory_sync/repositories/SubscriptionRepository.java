package com.tartaritech.inventory_sync.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tartaritech.inventory_sync.entities.Subscription;

public interface SubscriptionRepository extends JpaRepository <Subscription, String> {
    
}
