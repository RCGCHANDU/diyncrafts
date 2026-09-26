package com.diyncrafts.web.app.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.diyncrafts.web.app.dto.GuideCreateRequest;
import com.diyncrafts.web.app.dto.GuideResponse;
import com.diyncrafts.web.app.dto.GuideUpdateRequest;
import com.diyncrafts.web.app.service.GuideService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/guides")
public class GuideController {

    private final GuideService guideService;

    public GuideController(GuideService guideService) {
        this.guideService = guideService;
    }

    @GetMapping
    public List<GuideResponse> getAllGuides(@RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return guideService.getGuides(page, size);
    }

    @GetMapping("/{id}")
    public GuideResponse getGuide(@PathVariable Long id) {
        return guideService.getGuide(id);
    }

    @GetMapping("/video/{videoId}")
    public List<GuideResponse> getGuidesByVideo(@PathVariable Long videoId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return guideService.getGuidesByVideo(videoId, page, size);
    }

    @GetMapping("/user")
    public List<GuideResponse> getAuthenticatedUserGuides(Authentication authentication) {
        return guideService.getGuidesOf(authentication);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GuideResponse> createGuide(@Valid @RequestBody GuideCreateRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(guideService.createGuide(request, authentication));
    }

    @PutMapping("/{id}")
    public GuideResponse updateGuide(@PathVariable Long id, @Valid @ModelAttribute GuideUpdateRequest request,
            @RequestPart(required = false) MultipartFile imageFile, Authentication authentication)
            throws IOException {
        return guideService.updateGuide(id, request, imageFile, authentication);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGuide(@PathVariable Long id, Authentication authentication) {
        guideService.deleteGuide(id, authentication);
        return ResponseEntity.noContent().build();
    }
}
