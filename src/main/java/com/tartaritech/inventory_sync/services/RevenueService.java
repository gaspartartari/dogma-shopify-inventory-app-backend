package com.tartaritech.inventory_sync.services;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tartaritech.inventory_sync.dtos.RevenueDataPointDTO;
import com.tartaritech.inventory_sync.entities.RevenueCache;
import com.tartaritech.inventory_sync.repositories.RevenueCacheRepository;

@Service
public class RevenueService {

    private final RevenueCacheRepository revenueCacheRepository;
    private final Logger logger = LoggerFactory.getLogger(RevenueService.class);
    
    private static final DateTimeFormatter OUTPUT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    public RevenueService(RevenueCacheRepository revenueCacheRepository) {
        this.revenueCacheRepository = revenueCacheRepository;
    }

    @Transactional(readOnly = true)
    public List<RevenueDataPointDTO> getRevenueOverTime(LocalDate startDate, LocalDate endDate) {
        logger.info("Fetching revenue data from cache for period {} to {}", startDate, endDate);
        
        // Format dates as YYYY-MM for cache lookup
        String startYearMonth = startDate.format(OUTPUT_FORMATTER);
        String endYearMonth = endDate.format(OUTPUT_FORMATTER);
        
        // Fetch cached revenue data
        List<RevenueCache> cachedData = revenueCacheRepository
            .findByYearMonthBetweenOrderByYearMonthAsc(startYearMonth, endYearMonth);
        
        logger.debug("Found {} cached revenue entries", cachedData.size());
        
        // Convert to DTO
        List<RevenueDataPointDTO> result = cachedData.stream()
            .map(cache -> new RevenueDataPointDTO(
                cache.getYearMonth(), 
                cache.getTotalRevenue().doubleValue()
            ))
            .collect(Collectors.toList());
        
        // Fill in missing months with zero revenue
        result = fillMissingMonths(result, startDate, endDate);
        
        logger.info("Returning {} data points for revenue over time", result.size());
        return result;
    }
    
    private List<RevenueDataPointDTO> fillMissingMonths(List<RevenueDataPointDTO> data, 
                                                         LocalDate startDate, 
                                                         LocalDate endDate) {
        if (data.isEmpty()) {
            return new ArrayList<>();
        }
        
        Map<String, BigDecimal> revenueMap = data.stream()
            .collect(Collectors.toMap(
                RevenueDataPointDTO::getYearMonth,
                dto -> dto.getTotalRevenue(),
                (a, b) -> a
            ));
        
        List<RevenueDataPointDTO> filledData = new ArrayList<>();
        LocalDate current = startDate.withDayOfMonth(1);
        LocalDate end = endDate.withDayOfMonth(1);
        
        while (!current.isAfter(end)) {
            String yearMonth = current.format(OUTPUT_FORMATTER);
            BigDecimal revenue = revenueMap.getOrDefault(yearMonth, BigDecimal.ZERO);
            filledData.add(new RevenueDataPointDTO(yearMonth, revenue.doubleValue()));
            current = current.plusMonths(1);
        }
        
        return filledData;
    }
}

