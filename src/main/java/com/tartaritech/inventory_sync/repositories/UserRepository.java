package com.tartaritech.inventory_sync.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.tartaritech.inventory_sync.entities.User;
import com.tartaritech.inventory_sync.projections.UserDetailsProjection;



public interface UserRepository extends JpaRepository<User,Long> {
    @Query("""
                SELECT DISTINCT u FROM User u
                WHERE u.email = :email
            """)
    Optional<User> findByEmail(String email);

    @Query(nativeQuery = true, value = """
            SELECT u.email AS username, u.password, r.id AS roleId, r.authority
            FROM tb_user u
            INNER JOIN tb_user_role ur ON u.id = ur.user_id
            INNER JOIN tb_role r ON r.id = ur.role_id
            WHERE u.email = :email
            """)
    List<UserDetailsProjection> searchUserAndRolesByEmail(String email);

}