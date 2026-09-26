package com.diyncrafts.web.app.dto;

/**
 * Combined video search. Every non-blank field narrows the result:
 * {@code text} is a relevance-ranked full-text query over title, description and materials;
 * {@code category}, {@code difficulty} and {@code user} are exact (case-insensitive) filters;
 * {@code material} must match one of the listed materials. With no criteria, all videos are returned,
 * newest first.
 */
public record VideoSearchCriteria(String text, String title, String category, String difficulty, String material,
        String user) {

    public static VideoSearchCriteria empty() {
        return new VideoSearchCriteria(null, null, null, null, null, null);
    }

    public VideoSearchCriteria withText(String value) {
        return new VideoSearchCriteria(value, title, category, difficulty, material, user);
    }

    public VideoSearchCriteria withTitle(String value) {
        return new VideoSearchCriteria(text, value, category, difficulty, material, user);
    }

    public VideoSearchCriteria withCategory(String value) {
        return new VideoSearchCriteria(text, title, value, difficulty, material, user);
    }

    public VideoSearchCriteria withDifficulty(String value) {
        return new VideoSearchCriteria(text, title, category, value, material, user);
    }

    public VideoSearchCriteria withMaterial(String value) {
        return new VideoSearchCriteria(text, title, category, difficulty, value, user);
    }

    public VideoSearchCriteria withUser(String value) {
        return new VideoSearchCriteria(text, title, category, difficulty, material, value);
    }
}
