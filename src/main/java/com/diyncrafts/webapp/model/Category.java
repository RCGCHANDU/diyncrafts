package com.diyncrafts.webapp.model;

import lombok.Data;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import java.util.List;


@Data
@Entity
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Name is required")
    private String name;

    private String description;

    @ManyToOne
    @JoinColumn(name = "parent_category_id")
    private Category parentCategory; // Parent category for hierarchy

    @OneToMany(mappedBy = "parentCategory", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Category> subcategories; // Subcategories
}