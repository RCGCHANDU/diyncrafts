package com.diyncrafts.web.app.service;

import java.time.Clock;
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
import com.diyncrafts.web.app.repository.jpa.VideoDailyViewsRepository;
import com.diyncrafts.web.app.repository.jpa.VideoRepository;

@Service
public class CategoryService {

    static final int WINDOW_DAYS = 15;

    private final CategoryRepository categoryRepository;
    private final VideoRepository videoRepository;
    private final VideoDailyViewsRepository dailyViewsRepository;
    private final Clock clock;

    public CategoryService(CategoryRepository categoryRepository, VideoRepository videoRepository,
            VideoDailyViewsRepository dailyViewsRepository, Clock clock) {
        this.categoryRepository = categoryRepository;
        this.videoRepository = videoRepository;
        this.dailyViewsRepository = dailyViewsRepository;
        this.clock = clock;
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

    /**
     * Lifetime views per category plus growth: views in the last {@value #WINDOW_DAYS} days (including
     * today) compared with the {@value #WINDOW_DAYS} days before that, in percent. When the earlier
     * window has no views, growth is 100 if there are recent views and 0 otherwise.
     */
    @Transactional(readOnly = true)
    public List<CategoryStats> getCategoryStats() {
        LocalDate today = LocalDate.now(clock);
        LocalDate recentStart = today.minusDays(WINDOW_DAYS - 1);
        LocalDate previousEnd = recentStart.minusDays(1);
        LocalDate previousStart = previousEnd.minusDays(WINDOW_DAYS - 1);
        List<CategoryStats> stats = new ArrayList<>();
        for (Category category : categoryRepository.findAll(Sort.by("id"))) {
            Long categoryId = category.getId();
            long total = nullToZero(videoRepository.sumViewsByCategoryId(categoryId));
            long recent = dailyViewsRepository.sumViewsForCategory(categoryId, recentStart, today);
            long previous = dailyViewsRepository.sumViewsForCategory(categoryId, previousStart, previousEnd);
            stats.add(new CategoryStats(categoryId, total, growth(recent, previous)));
        }
        return stats;
    }

    static double growth(long recent, long previous) {
        if (previous == 0) {
            return recent > 0 ? 100.0 : 0.0;
        }
        double percent = ((double) (recent - previous) / previous) * 100;
        return Math.round(percent * 100.0) / 100.0;
    }

    private static long nullToZero(Long value) {
        return value == null ? 0 : value;
    }
}
