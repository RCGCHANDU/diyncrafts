package com.diyncrafts.webapp.controller;

import com.diyncrafts.webapp.model.Category;
import com.diyncrafts.webapp.service.CategoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Category> createCategory(
            @RequestBody Category category,
            @RequestParam(required = false) Long parentId) {
        return ResponseEntity.ok(categoryService.createCategory(category, parentId));
    }

    @GetMapping("/top-level")
    public ResponseEntity<List<Category>> getTopLevelCategories() {
        return ResponseEntity.ok(categoryService.getTopLevelCategories());
    }

    @GetMapping("/subcategories/{parentId}")
    public ResponseEntity<List<Category>> getSubcategories(@PathVariable Long parentId) {
        return ResponseEntity.ok(categoryService.getSubcategories(parentId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Category> updateCategory(@PathVariable Long id, @RequestBody Category updatedCategory) {
        return ResponseEntity.ok(categoryService.updateCategory(id, updatedCategory));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }
}