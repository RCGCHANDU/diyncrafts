package com.diyncrafts.web.app.model;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Application user. Never serialize this entity directly: it holds the password hash.
 */
@Getter
@Setter
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ERole role;

    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean enabled;

    public enum ERole {
        ROLE_USER, ROLE_ADMIN
    }

    @Override
    public String toString() {
        return "User[id=" + id + ", username=" + username + "]";
    }
}
