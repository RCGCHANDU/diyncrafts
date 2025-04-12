package com.diyncrafts.transcoding.service.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.diyncrafts.transcoding.service.model.Task;
import com.diyncrafts.transcoding.service.service.TaskService;

@RestController
@RequestMapping("/api/transcode")
public class FileUploadController {
    @Autowired
    private TaskService taskService;

    @PostMapping("/upload")
    public ResponseEntity<Task> upload(@RequestParam("file") MultipartFile file) {
        String taskId = taskService.initiateTranscoding(file);
        return ResponseEntity.ok(taskService.getTask(taskId));
    }

    @GetMapping("/status/{taskId}")
    public Task getTaskStatus(@PathVariable String taskId) {
        return taskService.getTask(taskId);
    }
}