package com.diyncrafts.web.app.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Partial update: null fields are left unchanged, but a supplied name may not be blank.
 */
public record CategoryUpdateRequest(
        @Size(max = 100) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String name,
        @Size(max = 255) String description) {
}
