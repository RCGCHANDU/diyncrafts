package com.diyncrafts.web.app.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.diyncrafts.web.app.dto.CategoryRequest;
import com.diyncrafts.web.app.dto.CategoryResponse;
import com.diyncrafts.web.app.dto.CategoryStats;
import com.diyncrafts.web.app.dto.CategoryUpdateRequest;
import com.diyncrafts.web.app.exceptions.ConflictException;
import com.diyncrafts.web.app.exceptions.ResourceNotFoundException;
import com.diyncrafts.web.app.model.Category;
import com.diyncrafts.web.app.repository.jpa.CategoryRepository;
import com.diyncrafts.web.app.repository.jpa.VideoRepository;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final VideoRepository videoRepository;

    public CategoryService(CategoryRepository categoryRepository, VideoRepository videoRepository) {
        this.categoryRepository = categoryRepository;
        this.videoRepository = videoRepository;
    }

    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        String name = request.name().trim();
        if (categoryRepository.existsByName(name)) {
            throw new ConflictException("Category name already exists.");
        }
        Category category = new Category();
        category.setName(name);
        category.setDescription(request.description());
        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse updateCategory(Long id, CategoryUpdateRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found."));
        if (request.name() != null) {
            String name = request.name().trim();
            if (!category.getName().equals(name) && categoryRepository.existsByName(name)) {
                throw new ConflictException("Category name already exists.");
            }
            category.setName(name);
        }
        if (request.description() != null) {
            category.setDescription(request.description());
        }
        return CategoryResponse.from(category);
    }

    @Transactional
    public void deleteCategory(Long id) {
        if (!categoryRepository.existsById(id)) {
            throw new ResourceNotFoundException("Category not found.");
        }
        if (videoRepository.existsByCategoryId(id)) {
            throw new ConflictException("Category is still assigned to videos.");
        }
        categoryRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAll(Sort.by("name")).stream().map(CategoryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryStats> getCategoryStats() {
        List<CategoryStats> stats = new ArrayList<>();
        LocalDate now = LocalDate.now();
        LocalDate lastWeek = now.minusDays(15);
        LocalDate twoWeeksAgo = now.minusDays(30);
        for (Category category : categoryRepository.findAll(Sort.by("id"))) {
            Long categoryId = category.getId();
            long total = nullToZero(videoRepository.sumViewsByCategoryId(categoryId));
            long recent = nullToZero(videoRepository.sumViewsBetweenDates(categoryId, lastWeek, now));
            long previous = nullToZero(videoRepository.sumViewsBetweenDates(categoryId, twoWeeksAgo, lastWeek));
            double growth = previous == 0 ? 100.0
                    : Math.round(((double) (recent - previous) / previous) * 100 * 100.0) / 100.0;
            stats.add(new CategoryStats(categoryId, total, growth));
        }
        return stats;
    }

    private static long nullToZero(Long value) {
        return value == null ? 0 : value;
    }
}
