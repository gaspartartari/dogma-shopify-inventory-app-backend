package com.tartaritech.inventory_sync.controllers;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tartaritech.inventory_sync.dtos.OrderWithDetailsDTO;
import com.tartaritech.inventory_sync.services.OrderService;

@RestController
@RequestMapping("/api/orders")
@CrossOrigin(origins = "*")
public class OrdersController {
    
    private final OrderService orderService;
    private final Logger logger = LoggerFactory.getLogger(OrdersController.class);
    
    public OrdersController(OrderService orderService) {
        this.orderService = orderService;
    }
    
    @GetMapping
    public ResponseEntity<List<OrderWithDetailsDTO>> getAllOrders() {
        try {
            logger.info("Requisição para listar todos os pedidos");
            List<OrderWithDetailsDTO> orders = orderService.getAllOrdersWithDetails();
            logger.info("Retornando {} pedidos", orders.size());
            return ResponseEntity.ok(orders);
        } catch (Exception e) {
            logger.error("Erro ao buscar pedidos", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}

