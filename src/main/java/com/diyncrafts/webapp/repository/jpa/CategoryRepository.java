package com.diyncrafts.webapp.repository.jpa;

import com.diyncrafts.webapp.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findByParentCategoryIsNull(); // Fetch top-level categories
    List<Category> findByParentCategory(Category parentCategory); // Fetch subcategories
}