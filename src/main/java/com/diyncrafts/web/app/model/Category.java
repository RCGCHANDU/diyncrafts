package com.diyncrafts.web.app.model;

import lombok.Getter;
import lombok.Setter;
import jakarta.persistence.*;



@Getter
@Setter
@Entity
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String description;
}