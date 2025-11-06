package com.tartaritech.inventory_sync.dtos;

import java.util.ArrayList;
import java.util.List;

import com.tartaritech.inventory_sync.entities.User;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserDTO {

    private Long id;
    private String email;
    private String password;
    private List<String> authorities = new ArrayList<>();


    public UserDTO() {

    }

    public UserDTO(User entity) {
        id = entity.getId();

        this.email = entity.getEmail();
        this.password = entity.getPassword();
        if (entity.getRoles() != null) {
            entity.getRoles().forEach(role -> this.authorities.add(role.getAuthority()));
        }
    }
}
