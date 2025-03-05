package com.diyncrafts.webapp.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.diyncrafts.webapp.model.Video;
import com.diyncrafts.webapp.repository.es.SearchRepository;

@Service
public class SearchService {

    @Autowired
    private SearchRepository searchRepository;

    public List<Video> searchVideos(String keyword, String category, String difficultyLevel, List<String> materialsUsed) {
        if (keyword != null && !keyword.isEmpty()) {
            return searchRepository.findByTitleContainingOrDescriptionContaining(keyword, keyword);
        } else if (category != null && !category.isEmpty()) {
            return searchRepository.findByCategory(category);
        } else if (difficultyLevel != null && !difficultyLevel.isEmpty()) {
            return searchRepository.findByDifficultyLevel(difficultyLevel);
        } else if (materialsUsed != null && !materialsUsed.isEmpty()) {
            return searchRepository.findByMaterialsUsedIn(materialsUsed);
        } else {
            return (List<Video>) searchRepository.findAll();
        }
    }
}