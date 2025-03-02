package com.diyncrafts.webapp.service;

import com.diyncrafts.webapp.model.Guide;
import com.diyncrafts.webapp.repository.GuideRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GuideService {

    @Autowired
    private GuideRepository guideRepository;

    public Guide createGuide(Guide guide) {
        return guideRepository.save(guide);
    }

    public List<Guide> getGuidesByVideoId(String videoId) {
        return guideRepository.findByVideoId(videoId);
    }

    public Guide updateGuide(Long id, Guide updatedGuide) {
        Guide existingGuide = guideRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Guide not found"));
        existingGuide.setTitle(updatedGuide.getTitle());
        existingGuide.setContent(updatedGuide.getContent());
        existingGuide.setImageUrl(updatedGuide.getImageUrl());
        return guideRepository.save(existingGuide);
    }

    public void deleteGuide(Long id) {
        guideRepository.deleteById(id);
    }
}