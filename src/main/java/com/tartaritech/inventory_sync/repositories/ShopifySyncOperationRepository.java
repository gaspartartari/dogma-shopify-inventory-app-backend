package com.tartaritech.inventory_sync.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.tartaritech.inventory_sync.entities.ShopifySyncOperation;

@Repository
public interface ShopifySyncOperationRepository extends JpaRepository<ShopifySyncOperation, Long> {
    
    List<ShopifySyncOperation> findByStatusAndRetryCountLessThan(String status, Integer maxRetries);
}
