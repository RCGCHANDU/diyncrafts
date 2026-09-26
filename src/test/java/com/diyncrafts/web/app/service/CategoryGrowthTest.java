package com.diyncrafts.web.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CategoryGrowthTest {

    @Test
    void growthPercentages() {
        assertThat(CategoryService.growth(6, 4)).isEqualTo(50.0);
        assertThat(CategoryService.growth(2, 4)).isEqualTo(-50.0);
        assertThat(CategoryService.growth(1, 3)).isEqualTo(-66.67);
        assertThat(CategoryService.growth(0, 0)).isEqualTo(0.0);
        assertThat(CategoryService.growth(5, 0)).isEqualTo(100.0);
        assertThat(CategoryService.growth(0, 5)).isEqualTo(-100.0);
    }
}
