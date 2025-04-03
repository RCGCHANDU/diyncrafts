package com.diyncrafts.webapp.service;

import com.diyncrafts.webapp.model.Category;
import com.diyncrafts.webapp.repository.jpa.CategoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class CategoryService {

    @Autowired
    private CategoryRepository categoryRepository;

    // Create category with uniqueness check
    @Transactional
    public Category createCategory(Category category) {
        if (categoryRepository.existsByName(category.getName())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Category name already exists");
        }
        return categoryRepository.save(category);
    }

    // Update category with null-safe field updates
    @Transactional
    public Category updateCategory(Long id, Category updatedCategory) {
        Category existingCategory = categoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));

        if (updatedCategory.getName() != null) {
            if (!existingCategory.getName().equals(updatedCategory.getName()) && 
                categoryRepository.existsByName(updatedCategory.getName())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Category name already exists");
            }
            existingCategory.setName(updatedCategory.getName());
        }

        if (updatedCategory.getDescription() != null) {
            existingCategory.setDescription(updatedCategory.getDescription());
        }

        return categoryRepository.save(existingCategory);
    }

    // Delete category with existence check
    @Transactional
    public void deleteCategory(Long id) {
        if (!categoryRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found");
        }
        categoryRepository.deleteById(id);
    }

    // Get all categories
    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }
}