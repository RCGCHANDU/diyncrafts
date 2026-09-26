package com.diyncrafts.web.app.dto;

/**
 * Per-category view statistics.
 *
 * @param viewCount total lifetime views of the category's videos
 * @param growth    percentage change in views between the two most recent comparison windows
 */
public record CategoryStats(long categoryId, long viewCount, double growth) {
}
