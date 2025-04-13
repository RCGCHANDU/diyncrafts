package com.diyncrafts.web.app.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.diyncrafts.web.app.dto.VideoUploadRequest;
import com.diyncrafts.web.app.model.Task;
import com.diyncrafts.web.app.service.TaskService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/transcode")
public class FileUploadController {
    @Autowired
    private TaskService taskService;

    @PostMapping("/upload")
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<Task> upload(
        @RequestParam("file") MultipartFile file
        ) {
        String taskId = taskService.initiateTranscoding(file);
        return ResponseEntity.ok(taskService.getTask(taskId));
    }

    @GetMapping("/status/{taskId}")
    @PreAuthorize("hasRole('ROLE_USER')")
    public Task getTaskStatus(@PathVariable String taskId) {
        return taskService.getTask(taskId);
    }
}