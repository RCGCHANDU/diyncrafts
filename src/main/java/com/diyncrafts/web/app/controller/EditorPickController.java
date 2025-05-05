package com.diyncrafts.web.app.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.diyncrafts.web.app.model.EditorPick;
import com.diyncrafts.web.app.service.EditorPickService;


@RestController
@RequestMapping("/api/editor-pick")
public class EditorPickController {
    
    private final EditorPickService editorPickService;

    public EditorPickController(EditorPickService editorPickService) {
        this.editorPickService = editorPickService;
    }

    // Set Editor's Pick
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EditorPick> setEditorPick(@RequestParam String videoId) {
        Long editorPickId = Long.parseLong(videoId);
        if (videoId == null) {
            throw new IllegalArgumentException("videoId is required");
        }

        EditorPick pick = editorPickService.setEditorPick(editorPickId);
        return ResponseEntity.ok(pick);
    }

    // Get current Editor's Pick
    @GetMapping
    public ResponseEntity<?> getCurrentEditorPick() {
        return editorPickService.getCurrentEditorPick()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

}
