package com.diyncrafts.webapp.controller;



import com.diyncrafts.webapp.model.Guide;
import com.diyncrafts.webapp.service.GuideService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/guides")
public class GuideController {

    private final GuideService guideService;

    public GuideController(GuideService guideService) {
        this.guideService = guideService;
    }

    @PostMapping
    public ResponseEntity<Guide> createGuide(@RequestBody Guide guide) {
        return ResponseEntity.ok(guideService.createGuide(guide));
    }

    @GetMapping("/video/{videoId}")
    public ResponseEntity<List<Guide>> getGuidesByVideoId(@PathVariable String videoId) {
        return ResponseEntity.ok(guideService.getGuidesByVideoId(videoId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Guide> updateGuide(@PathVariable Long id, @RequestBody Guide updatedGuide) {
        return ResponseEntity.ok(guideService.updateGuide(id, updatedGuide));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGuide(@PathVariable Long id) {
        guideService.deleteGuide(id);
        return ResponseEntity.noContent().build();
    }
}