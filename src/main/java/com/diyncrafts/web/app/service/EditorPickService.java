package com.diyncrafts.web.app.service;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.diyncrafts.web.app.model.EditorPick;
import com.diyncrafts.web.app.model.Video;
import com.diyncrafts.web.app.repository.jpa.EditorPickRepository;
import com.diyncrafts.web.app.repository.jpa.VideoRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EditorPickService {

    private final VideoRepository videoRepository;
    private final EditorPickRepository editorPickRepository;

    public EditorPick setEditorPick(Long videoId) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new RuntimeException("Video not found"));

        EditorPick newPick = new EditorPick();
        newPick.setVideo(video);

        // Optional: Remove previous pick
        editorPickRepository.findTopByOrderByIdDesc().ifPresent(pick -> editorPickRepository.deleteById(pick.getId()));

        return editorPickRepository.save(newPick);
    }

    public Optional<EditorPick> getCurrentEditorPick() {
        return editorPickRepository.findTopByOrderByIdDesc();
    }
}