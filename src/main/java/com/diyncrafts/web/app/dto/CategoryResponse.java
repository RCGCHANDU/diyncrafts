package com.diyncrafts.web.app.dto;

import com.diyncrafts.web.app.model.Category;

public record CategoryResponse(Long id, String name, String description) {

    public static CategoryResponse from(Category category) {
        return category == null ? null
                : new CategoryResponse(category.getId(), category.getName(), category.getDescription());
    }
}
