package com.diyncrafts.web.app.transcoding;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.diyncrafts.web.app.transcoding.FfmpegCommandBuilder.PlannedRendition;
import com.diyncrafts.web.app.transcoding.FfmpegCommandBuilder.Size;

class FfmpegCommandBuilderTest {

    private static final Path INPUT = Path.of("/work/t1/input");
    private static final Path OUTPUT = Path.of("/work/t1/output");

    private final FfmpegCommandBuilder cpu = new FfmpegCommandBuilder(
            TranscodingTestSettings.properties(Path.of("/work"), VideoEncoder.LIBX264));
    private final FfmpegCommandBuilder gpu = new FfmpegCommandBuilder(
            TranscodingTestSettings.properties(Path.of("/work"), VideoEncoder.H264_NVENC));

    @Test
    void argumentsNeverContainShellQuotes() {
        List<String> cmd = cpu.dash(Path.of("/work/my video/input"), OUTPUT, new MediaInfo(1920, 1080, 60, true));
        assertThat(cmd).noneMatch(arg -> arg.contains("\"") || arg.contains("'"));
        assertThat(cmd).contains("/work/my video/input");
        assertThat(cmd.get(cmd.size() - 1)).isEqualTo("/work/t1/output/manifest.mpd");
        assertThat(cmd).containsSequence("-init_seg_name", "init_$RepresentationID$.m4s");
        assertThat(cmd).containsSequence("-adaptation_sets", "id=0,streams=v id=1,streams=a");
    }

    @Test
    void cpuEncoderIsDefaultPathAndNvencIsOptional() {
        List<String> x264 = cpu.dash(INPUT, OUTPUT, new MediaInfo(1280, 720, 10, true));
        assertThat(x264).containsSequence("-c:v:0", "libx264", "-preset:v:0", "veryfast");
        assertThat(x264).doesNotContain("h264_nvenc");

        List<String> nvenc = gpu.dash(INPUT, OUTPUT, new MediaInfo(1280, 720, 10, true));
        assertThat(nvenc).containsSequence("-c:v:0", "h264_nvenc", "-preset:v:0", "p5", "-tune:v:0", "hq");
        assertThat(nvenc).doesNotContain("libx264");
    }

    @Test
    void videosWithoutAudioDoNotMapAnAudioStream() {
        List<String> cmd = cpu.dash(INPUT, OUTPUT, new MediaInfo(1280, 720, 10, false));
        assertThat(cmd).doesNotContain("0:a:0", "aac");
        assertThat(cmd).containsSequence("-adaptation_sets", "id=0,streams=v");
    }

    @Test
    void perStreamRateControlUsesStreamSpecifiers() {
        List<String> cmd = cpu.dash(INPUT, OUTPUT, new MediaInfo(1920, 1080, 10, true));
        assertThat(cmd).containsSequence("-b:v:0", "5M", "-maxrate:v:0", "6M", "-bufsize:v:0", "10M");
        assertThat(cmd).containsSequence("-b:v:3", "800k", "-maxrate:v:3", "1M", "-bufsize:v:3", "2M");
        assertThat(cmd).doesNotContain("-maxrate", "-bufsize");
        assertThat(cmd).containsSequence("-force_key_frames", "expr:gte(t,n_forced*7)");
    }

    @Test
    void landscapeSourceKeepsAspectRatioAndIsNotUpscaled() {
        List<PlannedRendition> plan = cpu.plan(new MediaInfo(1280, 720, 10, true));
        assertThat(plan).extracting(PlannedRendition::size)
                .containsExactly(new Size(1280, 720), new Size(854, 480), new Size(640, 360));
        assertThat(cpu.dash(INPUT, OUTPUT, new MediaInfo(1280, 720, 10, true)))
                .anyMatch(arg -> arg.startsWith("[0:v]split=3[s0][s1][s2];[s0]scale=1280:720:flags=lanczos"));
    }

    @Test
    void portraitSourceScalesByItsShortSide() {
        List<PlannedRendition> plan = cpu.plan(new MediaInfo(1080, 1920, 10, false));
        assertThat(plan).extracting(PlannedRendition::size).containsExactly(
                new Size(1080, 1920), new Size(720, 1280), new Size(480, 854), new Size(360, 640));
    }

    @Test
    void unusualAspectRatiosAreNotStretched() {
        // 4:3 and ultra-wide sources keep their shape (the old command forced 16:9).
        assertThat(FfmpegCommandBuilder.scale(new MediaInfo(640, 480, 0, false), 360)).isEqualTo(new Size(480, 360));
        assertThat(FfmpegCommandBuilder.scale(new MediaInfo(2560, 1080, 0, false), 720)).isEqualTo(new Size(1706, 720));
        Size odd = FfmpegCommandBuilder.scale(new MediaInfo(1001, 563, 0, false), 480);
        assertThat(odd.width() % 2).isZero();
        assertThat(odd.height() % 2).isZero();
    }

    @Test
    void tinySourcesAreEncodedOnceAtTheirOwnSize() {
        List<PlannedRendition> plan = cpu.plan(new MediaInfo(320, 241, 5, true));
        assertThat(plan).hasSize(1);
        // Rounded down to even so the source is never upscaled.
        assertThat(plan.get(0).size()).isEqualTo(new Size(318, 240));
        assertThat(plan.get(0).rendition().shortSide()).isEqualTo(360);
    }

    @Test
    void thumbnailKeepsAspectRatioWithoutUpscaling() {
        assertThat(FfmpegCommandBuilder.thumbnailSize(new MediaInfo(1920, 1080, 0, false))).isEqualTo(new Size(640, 360));
        assertThat(FfmpegCommandBuilder.thumbnailSize(new MediaInfo(1080, 1920, 0, false))).isEqualTo(new Size(360, 640));
        assertThat(FfmpegCommandBuilder.thumbnailSize(new MediaInfo(320, 240, 0, false))).isEqualTo(new Size(320, 240));
        List<String> cmd = cpu.thumbnail(INPUT, Path.of("/work/t1/thumbnail.jpg"), new MediaInfo(1920, 1080, 0.4, false));
        assertThat(cmd).containsSequence("-ss", "0.200").containsSequence("-vf", "scale=640:360");
    }
}
