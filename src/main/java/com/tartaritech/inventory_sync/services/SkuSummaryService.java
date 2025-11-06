package com.tartaritech.inventory_sync.services;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tartaritech.inventory_sync.dtos.SkuSummaryDTO;
import com.tartaritech.inventory_sync.repositories.ProductRepository;

@Service
public class SkuSummaryService {

    private final ProductRepository productRepository;
    private final Logger logger = LoggerFactory.getLogger(SkuSummaryService.class);

    public SkuSummaryService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public List<SkuSummaryDTO> getAllSkuSummaries() {
        logger.info("Fetching SKU summaries from database");
        
        List<Object[]> results = productRepository.findSkuSummaries();
        
        List<SkuSummaryDTO> summaries = results.stream()
                .map(row -> new SkuSummaryDTO(
                    (String) row[0],           // sku
                    (String) row[1],           // skuName
                    ((Number) row[2]).longValue(),  // totalQuantity
                    ((Number) row[3]).doubleValue(),
                    ((Number) row[4]).longValue()   // orderCount
                ))
                .collect(Collectors.toList());
        
        logger.info("Found {} SKU summaries", summaries.size());
        return summaries;
    }
}

