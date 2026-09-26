package com.diyncrafts.web.app.transcoding;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assumptions;

/**
 * Generates small test videos with the locally installed ffmpeg (tests are skipped without it).
 */
public final class MediaFixtures {

    private MediaFixtures() {
    }

    public static boolean ffmpegAvailable() {
        try {
            Process process = new ProcessBuilder("ffmpeg", "-version").redirectErrorStream(true).start();
            process.getInputStream().readAllBytes();
            return process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static void assumeFfmpeg() {
        Assumptions.assumeTrue(ffmpegAvailable(), "ffmpeg is not installed; skipping real transcoding test");
    }

    public static void withAudio(Path file) throws Exception {
        video(file, 640, 360, 2, true);
    }

    public static void withoutAudio(Path file) throws Exception {
        video(file, 640, 360, 2, false);
    }

    /**
     * @param audio include a sine-wave audio track
     */
    public static Path video(Path file, int width, int height, double seconds, boolean audio) throws Exception {
        List<String> cmd = new ArrayList<>(List.of("ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
                "-f", "lavfi", "-i", "testsrc=size=" + width + "x" + height + ":rate=25:duration=" + seconds));
        if (audio) {
            cmd.addAll(List.of("-f", "lavfi", "-i", "sine=frequency=440:duration=" + seconds, "-c:a", "aac", "-shortest"));
        }
        // Explicit format: the fixture may be written to a path without a file extension.
        cmd.addAll(List.of("-c:v", "libx264", "-preset", "ultrafast", "-pix_fmt", "yuv420p", "-f", "mp4",
                file.toString()));
        Process process = new ProcessBuilder(cmd).inheritIO().start();
        if (!process.waitFor(60, TimeUnit.SECONDS) || process.exitValue() != 0) {
            throw new IllegalStateException("Could not generate test video");
        }
        return file;
    }
}
