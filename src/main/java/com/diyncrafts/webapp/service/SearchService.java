package com.diyncrafts.webapp.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.diyncrafts.webapp.model.Video;
import com.diyncrafts.webapp.repository.VideoSearchRepository;

@Service
public class SearchService {

    @Autowired
    private VideoSearchRepository videoSearchRepository;

    public List<Video> searchVideos(String keyword, String category, String difficultyLevel, List<String> materialsUsed) {
        if (keyword != null && !keyword.isEmpty()) {
            return videoSearchRepository.findByTitleContainingOrDescriptionContaining(keyword, keyword);
        } else if (category != null && !category.isEmpty()) {
            return videoSearchRepository.findByCategory(category);
        } else if (difficultyLevel != null && !difficultyLevel.isEmpty()) {
            return videoSearchRepository.findByDifficultyLevel(difficultyLevel);
        } else if (materialsUsed != null && !materialsUsed.isEmpty()) {
            return videoSearchRepository.findByMaterialsUsedIn(materialsUsed);
        } else {
            return (List<Video>) videoSearchRepository.findAll();
        }
    }
}