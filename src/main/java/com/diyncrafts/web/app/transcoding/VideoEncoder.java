package com.diyncrafts.web.app.transcoding;

import java.util.List;

/**
 * H.264 encoder used for all renditions.
 */
public enum VideoEncoder {

    /** CPU encoding; available in every standard ffmpeg build. */
    LIBX264("libx264", "veryfast"),
    /** NVIDIA hardware encoding; requires a GPU and an ffmpeg build with NVENC. */
    H264_NVENC("h264_nvenc", "p5");

    private final String ffmpegName;
    private final String defaultPreset;

    VideoEncoder(String ffmpegName, String defaultPreset) {
        this.ffmpegName = ffmpegName;
        this.defaultPreset = defaultPreset;
    }

    public String ffmpegName() {
        return ffmpegName;
    }

    public String defaultPreset() {
        return defaultPreset;
    }

    /**
     * Encoder-specific options for output stream {@code index}.
     */
    List<String> streamOptions(int index, String preset) {
        return switch (this) {
            case LIBX264 -> List.of("-c:v:" + index, ffmpegName, "-preset:v:" + index, preset);
            case H264_NVENC -> List.of("-c:v:" + index, ffmpegName, "-preset:v:" + index, preset,
                    "-tune:v:" + index, "hq");
        };
    }
}
