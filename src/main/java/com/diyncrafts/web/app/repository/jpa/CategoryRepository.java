package com.diyncrafts.web.app.repository.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import com.diyncrafts.web.app.model.Category;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    Category findByName(String name);

    boolean existsByName(String name);
}
