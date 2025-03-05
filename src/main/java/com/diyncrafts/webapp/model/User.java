package com.diyncrafts.webapp.model;


import jakarta.persistence.*;
import lombok.Data;
import java.util.UUID;

@Data
@Entity
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

    public enum ERole {
        ROLE_USER, ROLE_ADMIN
    }
}