package com.diyncrafts.web.app.dto;

import com.diyncrafts.web.app.model.EditorPick;

public record EditorPickResponse(Long id, VideoResponse video) {

    public static EditorPickResponse from(EditorPick pick) {
        return new EditorPickResponse(pick.getId(), VideoResponse.from(pick.getVideo()));
    }
}
