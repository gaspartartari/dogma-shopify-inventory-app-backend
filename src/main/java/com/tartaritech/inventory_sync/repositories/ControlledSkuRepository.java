package com.tartaritech.inventory_sync.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tartaritech.inventory_sync.entities.ControlledSKu;

public interface ControlledSkuRepository extends JpaRepository<ControlledSKu, String>  {
    
}
