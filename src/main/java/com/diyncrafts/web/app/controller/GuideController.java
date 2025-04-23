package com.diyncrafts.web.app.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.diyncrafts.web.app.dto.GuideCreateRequest;
import com.diyncrafts.web.app.dto.GuideUpdateRequest;
import com.diyncrafts.web.app.model.Guide;
import com.diyncrafts.web.app.service.GuideService;

import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/guides")
@Validated
public class GuideController {

    private final GuideService guideService;

    public GuideController(GuideService guideService) {
        this.guideService = guideService;
    }

    // Create Guide
    @PostMapping(consumes = "application/json")
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<Guide> createGuide(
        @Valid @RequestBody GuideCreateRequest request) throws IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(guideService.createGuide(request, authentication));
    }

    // Get Guides by Video
    @GetMapping("/video/{videoId}")
    public ResponseEntity<List<Guide>> getGuidesByVideo(
        @PathVariable Long videoId,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        int offset = (page - 1) * size;
        return ResponseEntity.ok(guideService.getGuidesByVideoId(videoId, offset, size));
    }

    @GetMapping("/{id}") // Fixes the error
    @PreAuthorize("hasRole('ROLE_USER')") // Adjust permissions as needed
    public ResponseEntity<Guide> getGuide(@PathVariable Long id) {
        Guide guide = guideService.getGuideById(id);
        return ResponseEntity.ok(guide);
    }

    // Update Guide
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<Guide> updateGuide(
        @PathVariable Long id,
        @Valid @ModelAttribute GuideUpdateRequest request,
        @RequestPart(required = false) MultipartFile imageFile
    ) throws IOException {
        return ResponseEntity.ok(guideService.updateGuide(id, request, imageFile));
    }

    // Delete Guide
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<Void> deleteGuide(@PathVariable Long id) {
        guideService.deleteGuide(id);
        return ResponseEntity.noContent().build();
    }
}