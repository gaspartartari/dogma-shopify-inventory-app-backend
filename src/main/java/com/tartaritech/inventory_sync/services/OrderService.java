package com.tartaritech.inventory_sync.services;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tartaritech.inventory_sync.dtos.OrderWithDetailsDTO;
import com.tartaritech.inventory_sync.repositories.OrderRepository;

@Service
public class OrderService {
    
    private final OrderRepository orderRepository;
    private final Logger logger = LoggerFactory.getLogger(OrderService.class);
    
    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }
    
    @Transactional(readOnly = true)
    public List<OrderWithDetailsDTO> getAllOrdersWithDetails() {
        logger.info("Buscando todos os pedidos com detalhes");
        return orderRepository.findAll().stream()
            .map(OrderWithDetailsDTO::new)
            .collect(Collectors.toList());
    }
}

