package com.diyncrafts.web.app.repository.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.diyncrafts.web.app.model.Category;


@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    // Standard method for finding by name
    Category findByName(String name);

    // Check existence by name for uniqueness validation
    boolean existsByName(String name);

    // Custom query for case-insensitive search (if needed)
    @Query("SELECT c FROM Category c WHERE LOWER(c.name) = LOWER(:name)")
    Category findByNameIgnoreCase(@Param("name") String name);

    // Method to get all categories with pagination (if needed)
    // List<Category> findAll(Pageable pageable); // Already exists in JpaRepository
}