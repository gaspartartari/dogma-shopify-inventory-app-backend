package com.tartaritech.inventory_sync.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.tartaritech.inventory_sync.entities.Product;

public interface ProductRepository extends JpaRepository <Product, Long > {
    
    @Query("""
        SELECT p.controlledSku.sku, p.controlledSku.name, SUM(p.quantity), SUM(p.totalPrice),COUNT(DISTINCT p.order.id)
        FROM Product p
        WHERE p.controlledSku IS NOT NULL
        GROUP BY p.controlledSku.sku, p.controlledSku.name
        ORDER BY SUM(p.quantity) DESC
    """)
    List<Object[]> findSkuSummaries();
    
}
