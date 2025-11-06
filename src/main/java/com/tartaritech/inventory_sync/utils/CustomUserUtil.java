package com.tartaritech.inventory_sync.utils;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class CustomUserUtil {

    public String getLoggedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return "system";
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwtPrincipal) {
            String username = jwtPrincipal.getClaim("username");
            if (username != null)
                return username;
            String sub = jwtPrincipal.getSubject();
            return sub != null ? sub : "system";
        }
        if (principal instanceof String s) {
            return s;
        }
        return "system";
    }
}
