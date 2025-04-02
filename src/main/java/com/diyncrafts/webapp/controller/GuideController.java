package com.diyncrafts.webapp.controller;

import com.diyncrafts.webapp.model.Guide;
import com.diyncrafts.webapp.service.GuideService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/guides")
public class GuideController {

    private final GuideService guideService;

    public GuideController(GuideService guideService) {
        this.guideService = guideService;
    }

    @PostMapping(consumes = {"multipart/form-data"})
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<Guide> createGuide(
            @RequestParam("title") String title,
            @RequestParam("content") String content,
            @RequestParam("videoId") Long videoId,
            @RequestParam(value = "imageFile", required = false) MultipartFile imageFile) throws IOException {
        return ResponseEntity.ok(guideService.createGuide(title, content, videoId, imageFile));
    }

    @GetMapping("/video/{videoId}")
    public ResponseEntity<List<Guide>> getGuidesByVideoId(
        @PathVariable Long videoId,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "10") int limit) {
        
        int offset = (page - 1) * limit;
        return ResponseEntity.ok(guideService.getGuidesByVideoId(videoId, offset, limit));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<Guide> updateGuide(
            @PathVariable Long id,
            @RequestParam("title") String title,
            @RequestParam("content") String content,
            @RequestParam(value = "imageFile", required = false) MultipartFile imageFile) throws IOException {
        return ResponseEntity.ok(guideService.updateGuide(id, title, content, imageFile));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<Void> deleteGuide(@PathVariable Long id) {
        guideService.deleteGuide(id);
        return ResponseEntity.noContent().build();
    }
}