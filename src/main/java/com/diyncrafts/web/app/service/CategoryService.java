package com.diyncrafts.web.app.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.diyncrafts.web.app.dto.CategoryStats;
import com.diyncrafts.web.app.model.Category;
import com.diyncrafts.web.app.repository.jpa.CategoryRepository;
import com.diyncrafts.web.app.repository.jpa.VideoRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class CategoryService {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private VideoRepository videoRepository;

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

    public List<CategoryStats> getCategoryStats() {
        List<Category> categories = categoryRepository.findAll();
        List<CategoryStats> stats = new ArrayList<>();

        LocalDate now = LocalDate.now();
        LocalDate lastWeek = now.minusDays(15);
        LocalDate twoWeeksAgo = now.minusDays(30);

        for (Category category : categories) {
            Long categoryId = category.getId();

            Integer totalViews = videoRepository.sumViewsByCategoryId(categoryId);
            int total = (totalViews != null) ? totalViews : 0;

            Integer recentViews = videoRepository.sumViewsBetweenDates(categoryId, lastWeek, now);
            int recent = (recentViews != null) ? recentViews : 0;

            Integer previousViews = videoRepository.sumViewsBetweenDates(categoryId, twoWeeksAgo, lastWeek);
            int previous = (previousViews != null) ? previousViews : 0;

            double growth;
            if (previous == 0) {
                growth = 100.0; // Avoid division by zero
            } else {
                growth = ((double) (recent - previous) / previous) * 100;
                growth = Math.round(growth * 100.0) / 100.0; // Round to 2 decimal places
            }

            stats.add(new CategoryStats(categoryId.intValue(), total, growth));
        }

        return stats;
    }
}