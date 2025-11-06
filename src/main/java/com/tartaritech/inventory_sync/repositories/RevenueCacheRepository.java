package com.tartaritech.inventory_sync.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.tartaritech.inventory_sync.entities.RevenueCache;

@Repository
public interface RevenueCacheRepository extends JpaRepository<RevenueCache, Long> {
    
    @Query("SELECT r FROM RevenueCache r WHERE r.yearMonth >= :start AND r.yearMonth <= :end ORDER BY r.yearMonth ASC")
    List<RevenueCache> findByYearMonthBetweenOrderByYearMonthAsc(@Param("start") String start, @Param("end") String end);
    
    Optional<RevenueCache> findByYearMonth(String yearMonth);
}

