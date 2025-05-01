package com.diyncrafts.web.app.dto;

public class CategoryStats {
    private int categoryId;
    private int viewCount;
    private double growth;

    public CategoryStats(int categoryId, int viewCount, double growth) {
        this.categoryId = categoryId;
        this.viewCount = viewCount;
        this.growth = growth;
    }

    public int getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(int categoryId) {
        this.categoryId = categoryId;
    }

    public int getViewCount() {
        return viewCount;
    }

    public void setViewCount(int viewCount) {
        this.viewCount = viewCount;
    }

    public double getGrowth() {
        return growth;
    }

    public void setGrowth(double growth) {
        this.growth = growth;
    }
}