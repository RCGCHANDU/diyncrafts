package com.diyncrafts.web.app.dto;

import com.diyncrafts.web.app.model.Task;
import com.diyncrafts.web.app.model.Video;

public class VideoAndTaskResponse {
    private Video video;
    private Task task;
    
    public Video getVideo() {
        return video;
    }
    public void setVideo(Video video) {
        this.video = video;
    }
    public Task getTask() {
        return task;
    }
    public void setTask(Task task) {
        this.task = task;
    }
}