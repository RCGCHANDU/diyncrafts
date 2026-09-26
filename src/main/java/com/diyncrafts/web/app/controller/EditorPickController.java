package com.diyncrafts.web.app.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.diyncrafts.web.app.dto.EditorPickResponse;
import com.diyncrafts.web.app.service.EditorPickService;

import jakarta.validation.constraints.Positive;

@RestController
@RequestMapping("/api/editor-pick")
public class EditorPickController {

    private final EditorPickService editorPickService;

    public EditorPickController(EditorPickService editorPickService) {
        this.editorPickService = editorPickService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public EditorPickResponse setEditorPick(@RequestParam @Positive Long videoId) {
        return editorPickService.setEditorPick(videoId);
    }

    @GetMapping
    public ResponseEntity<EditorPickResponse> getCurrentEditorPick() {
        return editorPickService.getCurrentEditorPick()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
