package com.diyncrafts.webapp.model;


import jakarta.persistence.*;
import lombok.Data;
import java.util.UUID;

@Data
@Entity
@Table(name = "user_account")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private ERole role;

    @Column(nullable = false) // Add the 'enabled' column to match the query.
    private boolean enabled;  // Indicates if the user account is active

    public enum ERole {
        ROLE_USER, ROLE_ADMIN
    }
}