package com.diyncrafts.web.app.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.diyncrafts.web.app.dto.EditorPickResponse;
import com.diyncrafts.web.app.exceptions.ResourceNotFoundException;
import com.diyncrafts.web.app.model.EditorPick;
import com.diyncrafts.web.app.model.Video;
import com.diyncrafts.web.app.repository.jpa.EditorPickRepository;
import com.diyncrafts.web.app.repository.jpa.VideoRepository;

@Service
public class EditorPickService {

    private final VideoRepository videoRepository;
    private final EditorPickRepository editorPickRepository;

    public EditorPickService(VideoRepository videoRepository, EditorPickRepository editorPickRepository) {
        this.videoRepository = videoRepository;
        this.editorPickRepository = editorPickRepository;
    }

    /**
     * Replaces the current editor's pick; only one pick is kept.
     */
    @Transactional
    public EditorPickResponse setEditorPick(Long videoId) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new ResourceNotFoundException("Video not found."));
        editorPickRepository.deleteAllInBatch();
        EditorPick pick = new EditorPick();
        pick.setVideo(video);
        return EditorPickResponse.from(editorPickRepository.save(pick));
    }

    @Transactional(readOnly = true)
    public Optional<EditorPickResponse> getCurrentEditorPick() {
        return editorPickRepository.findTopByOrderByIdDesc().map(EditorPickResponse::from);
    }
}
