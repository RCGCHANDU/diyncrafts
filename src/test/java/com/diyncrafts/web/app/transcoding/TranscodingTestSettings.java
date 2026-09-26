package com.diyncrafts.web.app.transcoding;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import com.diyncrafts.web.app.config.TranscodingProperties;

final class TranscodingTestSettings {

    static final List<Rendition> LADDER = List.of(
            new Rendition(1080, "5M", "6M", "10M"),
            new Rendition(720, "3M", "3500k", "6M"),
            new Rendition(480, "1500k", "1800k", "3M"),
            new Rendition(360, "800k", "1M", "2M"));

    private TranscodingTestSettings() {
    }

    static TranscodingProperties properties(Path workDir, VideoEncoder encoder) {
        return new TranscodingProperties(workDir, "ffmpeg", "ffprobe", encoder, null, 7, Duration.ofMinutes(5), LADDER);
    }
}
