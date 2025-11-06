package com.tartaritech.inventory_sync.projections;

public interface UserDetailsProjection {
    String getUsername();

    String getPassword();

    Long getRoleId();

    String getAuthority();
}
