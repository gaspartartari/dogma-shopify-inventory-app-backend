package com.tartaritech.inventory_sync.services;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tartaritech.inventory_sync.dtos.ControlledSkuDTO;
import com.tartaritech.inventory_sync.entities.ControlledSKu;
import com.tartaritech.inventory_sync.repositories.ControlledSkuRepository;

@Service
public class ControlledSkuService {

    private final ControlledSkuRepository controlledSkuRepository;
    private final Logger logger = LoggerFactory.getLogger(ControlledSkuService.class);

    public ControlledSkuService(ControlledSkuRepository controlledSkuRepository) {
        this.controlledSkuRepository = controlledSkuRepository;
    }
    
    @Transactional(readOnly = true)
    public List<ControlledSkuDTO> getAllControlledSkus() {
        logger.info("Fetching all controlled SKUs");
        List<ControlledSKu> skus = controlledSkuRepository.findAll();
        return skus.stream()
                .map(ControlledSkuDTO::new)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public Optional<ControlledSkuDTO> getControlledSkuBySku(String sku) {
        logger.info("Fetching controlled SKU: {}", sku);
        return controlledSkuRepository.findById(sku)
                .map(ControlledSkuDTO::new);
    }
    
    @Transactional
    public ControlledSkuDTO createControlledSku(ControlledSkuDTO dto) {
        logger.info("Creating controlled SKU: {}", dto.getSku());
        
        // Check if SKU already exists
        if (controlledSkuRepository.existsById(dto.getSku())) {
            throw new IllegalArgumentException("SKU já existe: " + dto.getSku());
        }
        
        ControlledSKu entity = new ControlledSKu();
        entity.setSku(dto.getSku());
        entity.setName(dto.getName());
        
        ControlledSKu saved = controlledSkuRepository.save(entity);
        logger.info("Controlled SKU created successfully: {}", saved.getSku());
        
        return new ControlledSkuDTO(saved);
    }
    
    @Transactional
    public ControlledSkuDTO updateControlledSku(String sku, ControlledSkuDTO dto) {
        logger.info("Updating controlled SKU: {}", sku);
        
        ControlledSKu entity = controlledSkuRepository.findById(sku)
                .orElseThrow(() -> new IllegalArgumentException("SKU não encontrado: " + sku));
        
        // If SKU is being changed, check if new SKU already exists
        if (!sku.equals(dto.getSku()) && controlledSkuRepository.existsById(dto.getSku())) {
            throw new IllegalArgumentException("Novo SKU já existe: " + dto.getSku());
        }
        
        // Update fields
        entity.setName(dto.getName());
        
        // Note: Updating the ID (SKU) is complex in JPA. For now, we only allow updating the name.
        // If SKU needs to be changed, delete and create new one.
        
        ControlledSKu updated = controlledSkuRepository.save(entity);
        logger.info("Controlled SKU updated successfully: {}", updated.getSku());
        
        return new ControlledSkuDTO(updated);
    }
    
    @Transactional
    public void deleteControlledSku(String sku) {
        logger.info("Deleting controlled SKU: {}", sku);
        
        if (!controlledSkuRepository.existsById(sku)) {
            throw new IllegalArgumentException("SKU não encontrado: " + sku);
        }
        
        controlledSkuRepository.deleteById(sku);
        logger.info("Controlled SKU deleted successfully: {}", sku);
    }
}
