package com.tartaritech.inventory_sync.services;

import java.util.List;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tartaritech.inventory_sync.entities.Role;
import com.tartaritech.inventory_sync.entities.User;
import com.tartaritech.inventory_sync.repositories.UserRepository;
import com.tartaritech.inventory_sync.projections.UserDetailsProjection;
import com.tartaritech.inventory_sync.dtos.UserDTO;
import com.tartaritech.inventory_sync.utils.CustomUserUtil;

@Service
public class UserService implements UserDetailsService {


    private final UserRepository userRepository;
    private final CustomUserUtil customUserUtil;

    public UserService(UserRepository userRepository, CustomUserUtil customUserUtil ) {
        this.userRepository = userRepository;
        this.customUserUtil = customUserUtil;
    }


    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        List<UserDetailsProjection> result = userRepository.searchUserAndRolesByEmail(username);
        if (result.size() == 0)
            throw new UsernameNotFoundException("username " + username + " not found");

        User user = new User();
        user.setEmail(result.get(0).getUsername());
        user.setPassword(result.get(0).getPassword());
        for (UserDetailsProjection projection : result) {
            user.addRole(new Role(projection.getRoleId(), projection.getAuthority()));
        }

        return user;
    }

    protected User authenticated() {
        try {
            String username = customUserUtil.getLoggedUser();
            return userRepository.findByEmail(username).get();

        } catch (Exception e) {
            throw new UsernameNotFoundException("username not found");
        }
    }

    @Transactional(readOnly = true)
    public UserDTO getMe() {
        User user = authenticated();
        return new UserDTO(user);
    }

   

}