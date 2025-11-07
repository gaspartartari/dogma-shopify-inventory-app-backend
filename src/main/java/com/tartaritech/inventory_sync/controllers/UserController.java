package com.tartaritech.inventory_sync.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tartaritech.inventory_sync.dtos.UserDTO;
import com.tartaritech.inventory_sync.services.UserService;



@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserService userService;

    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_SUPERVISOR', 'MECHANIC', 'MECHANIC_SUPERVISOR')")
    @GetMapping("/me")
    public ResponseEntity<UserDTO> getMe() {
        UserDTO user = userService.getMe();
        return ResponseEntity.ok(user);
    }
}
