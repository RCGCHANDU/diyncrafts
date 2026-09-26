package com.diyncrafts.web.app.transcoding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs the generated commands with the real ffmpeg/ffprobe binaries (CPU encoder).
 */
class RealFfmpegTranscodingTest {

    private static final Pattern REPRESENTATION = Pattern.compile("<Representation[^>]*width=\"(\\d+)\" height=\"(\\d+)\"");

    @TempDir
    Path dir;

    private final MediaProcessRunner runner = new MediaProcessRunner();
    private FfmpegCommandBuilder commands;

    @BeforeAll
    static void requireFfmpeg() {
        MediaFixtures.assumeFfmpeg();
    }

    @Test
    void landscapeWithAudioProducesDashLadderWithAudio() throws Exception {
        commands = new FfmpegCommandBuilder(TranscodingTestSettings.properties(dir, VideoEncoder.LIBX264));
        Path input = MediaFixtures.video(dir.resolve("in.mp4"), 1280, 720, 3, true);
        MediaInfo media = probe(input);
        assertThat(media).isEqualTo(new MediaInfo(1280, 720, media.duration(), true));

        List<Double> progress = new ArrayList<>();
        Path output = Files.createDirectories(dir.resolve("out"));
        runner.run(commands.dash(input, output, media), dir.resolve("ffmpeg.log"), Duration.ofMinutes(2),
                new ProgressTracker(media.duration(), progress::add));

        String manifest = Files.readString(output.resolve("manifest.mpd"));
        assertThat(representations(manifest)).containsExactly("1280x720", "854x480", "640x360");
        assertThat(manifest).contains("contentType=\"audio\"");
        assertThat(Files.list(output).map(p -> p.getFileName().toString()))
                .anyMatch(name -> name.startsWith("init_")).anyMatch(name -> name.startsWith("chunk_"));
        assertThat(progress).isNotEmpty().isSorted().allMatch(p -> p <= 99);
    }

    @Test
    void portraitVideoWithoutAudioIsTranscoded() throws Exception {
        commands = new FfmpegCommandBuilder(TranscodingTestSettings.properties(dir, VideoEncoder.LIBX264));
        Path input = MediaFixtures.video(dir.resolve("portrait.mp4"), 720, 1280, 2, false);
        MediaInfo media = probe(input);
        assertThat(media.hasAudio()).isFalse();

        Path output = Files.createDirectories(dir.resolve("out"));
        runner.run(commands.dash(input, output, media), dir.resolve("ffmpeg.log"), Duration.ofMinutes(2), line -> { });

        String manifest = Files.readString(output.resolve("manifest.mpd"));
        assertThat(representations(manifest)).containsExactly("720x1280", "480x854", "360x640");
        assertThat(manifest).doesNotContain("contentType=\"audio\"");
    }

    @Test
    void fourByThreeSourceIsNotStretchedAndThumbnailIsGenerated() throws Exception {
        commands = new FfmpegCommandBuilder(TranscodingTestSettings.properties(dir, VideoEncoder.LIBX264));
        Path input = MediaFixtures.video(dir.resolve("small.mp4"), 400, 300, 2, false);
        MediaInfo media = probe(input);
        Path output = Files.createDirectories(dir.resolve("out"));
        runner.run(commands.dash(input, output, media), dir.resolve("ffmpeg.log"), Duration.ofMinutes(2), line -> { });
        // Smaller than every rung: encoded once at its own (even) size.
        assertThat(representations(Files.readString(output.resolve("manifest.mpd")))).containsExactly("400x300");

        Path thumbnail = dir.resolve("thumb.jpg");
        runner.run(commands.thumbnail(input, thumbnail, media), dir.resolve("ffmpeg.log"), Duration.ofMinutes(1),
                line -> { });
        assertThat(probe(thumbnail)).extracting(MediaInfo::width, MediaInfo::height).containsExactly(400, 300);
    }

    @Test
    void corruptInputFailsWithUserSafeMessage() throws Exception {
        commands = new FfmpegCommandBuilder(TranscodingTestSettings.properties(dir, VideoEncoder.LIBX264));
        Path input = Files.write(dir.resolve("broken.mp4"), "definitely not a video".getBytes());
        assertThatThrownBy(() -> probe(input))
                .isInstanceOf(TranscodingException.class)
                .hasMessage("The video could not be processed.");
        assertThat(Files.readString(dir.resolve("ffmpeg.log"))).isNotBlank();
    }

    @Test
    void processesExceedingTheTimeoutAreKilled() throws Exception {
        commands = new FfmpegCommandBuilder(TranscodingTestSettings.properties(dir, VideoEncoder.LIBX264));
        Path input = MediaFixtures.video(dir.resolve("in.mp4"), 1920, 1080, 10, true);
        MediaInfo media = probe(input);
        Path output = Files.createDirectories(dir.resolve("out"));
        assertThatThrownBy(() -> runner.run(commands.dash(input, output, media), dir.resolve("ffmpeg.log"),
                Duration.ofMillis(300), line -> { }))
                .isInstanceOf(TranscodingException.class)
                .hasMessage("Processing took too long and was stopped.");
    }

    private MediaInfo probe(Path file) throws Exception {
        return FfprobeParser.parse(runner.capture(commands.probe(file), dir.resolve("ffmpeg.log"), Duration.ofMinutes(1)));
    }

    private static List<String> representations(String manifest) {
        List<String> sizes = new ArrayList<>();
        Matcher matcher = REPRESENTATION.matcher(manifest);
        while (matcher.find()) {
            sizes.add(matcher.group(1) + "x" + matcher.group(2));
        }
        return sizes;
    }
}
