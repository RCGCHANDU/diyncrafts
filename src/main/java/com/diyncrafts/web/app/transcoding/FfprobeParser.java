package com.diyncrafts.web.app.transcoding;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Parses {@code ffprobe -print_format json -show_format -show_streams} output.
 */
final class FfprobeParser {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private FfprobeParser() {
    }

    static MediaInfo parse(String json) {
        JsonNode root;
        try {
            root = JSON.readTree(json);
        } catch (JacksonException e) {
            throw new TranscodingException("The uploaded file could not be read as a video.", e);
        }
        JsonNode video = null;
        boolean hasAudio = false;
        for (JsonNode stream : root.path("streams")) {
            String type = stream.path("codec_type").asString("");
            if ("video".equals(type) && video == null && !isAttachedPicture(stream)) {
                video = stream;
            } else if ("audio".equals(type)) {
                hasAudio = true;
            }
        }
        if (video == null || video.path("width").asInt(0) <= 0 || video.path("height").asInt(0) <= 0) {
            throw new TranscodingException("The uploaded file does not contain a video stream.");
        }
        int width = video.path("width").asInt();
        int height = video.path("height").asInt();
        // ffmpeg auto-rotates, so the output uses display orientation.
        if (Math.abs(rotation(video)) % 180 == 90) {
            int swap = width;
            width = height;
            height = swap;
        }
        double duration = parseDouble(root.path("format").path("duration").asString(""));
        return new MediaInfo(width, height, duration, hasAudio);
    }

    private static boolean isAttachedPicture(JsonNode stream) {
        return stream.path("disposition").path("attached_pic").asInt(0) == 1;
    }

    private static int rotation(JsonNode video) {
        for (JsonNode sideData : video.path("side_data_list")) {
            if (sideData.has("rotation")) {
                return sideData.path("rotation").asInt(0);
            }
        }
        return (int) parseDouble(video.path("tags").path("rotate").asString("0"));
    }

    private static double parseDouble(String value) {
        try {
            return value.isBlank() ? 0 : Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
