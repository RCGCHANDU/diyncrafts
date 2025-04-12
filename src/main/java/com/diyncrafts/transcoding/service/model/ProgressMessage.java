package com.diyncrafts.transcoding.service.model;

public class ProgressMessage {
    private String taskId;
    private double progress;

    // Constructor
    public ProgressMessage(String taskId, double progress) {
        this.taskId = taskId;
        this.progress = progress;
    }

    // Getters and Setters
    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public double getProgress() {
        return progress;
    }

    public void setProgress(double progress) {
        this.progress = progress;
    }
}