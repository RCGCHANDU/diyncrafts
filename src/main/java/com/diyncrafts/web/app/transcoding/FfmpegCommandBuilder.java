package com.diyncrafts.web.app.transcoding;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.diyncrafts.web.app.config.TranscodingProperties;

/**
 * Builds ffmpeg argument lists. Every element is one argv entry passed straight to
 * {@link ProcessBuilder} (no shell), so values are never quoted.
 */
public final class FfmpegCommandBuilder {

    static final int THUMBNAIL_LONG_SIDE = 640;

    private final TranscodingProperties properties;

    public FfmpegCommandBuilder(TranscodingProperties properties) {
        this.properties = properties;
    }

    /**
     * Picks the renditions for a source: all configured ones not larger than the source's short side
     * (no upscaling); a source smaller than every rendition is encoded once at its own size.
     */
    List<PlannedRendition> plan(MediaInfo media) {
        List<Rendition> ladder = properties.renditions().stream()
                .sorted((a, b) -> Integer.compare(b.shortSide(), a.shortSide()))
                .toList();
        List<PlannedRendition> planned = new ArrayList<>();
        for (Rendition rendition : ladder) {
            if (rendition.shortSide() <= media.shortSide()) {
                planned.add(new PlannedRendition(rendition, scale(media, rendition.shortSide())));
            }
        }
        if (planned.isEmpty()) {
            Rendition smallest = ladder.get(ladder.size() - 1);
            planned.add(new PlannedRendition(smallest, scale(media, evenDown(media.shortSide()))));
        }
        return planned;
    }

    /**
     * Scales so the short side becomes {@code shortSide}, preserving the aspect ratio; both
     * dimensions are rounded to even numbers as required by H.264 with 4:2:0 chroma.
     */
    static Size scale(MediaInfo media, int shortSide) {
        int target = nearestEven(shortSide);
        int longSide = nearestEven((double) Math.max(media.width(), media.height()) * target / media.shortSide());
        return media.isPortrait() ? new Size(target, longSide) : new Size(longSide, target);
    }

    public List<String> dash(Path input, Path outputDir, MediaInfo media) {
        List<PlannedRendition> renditions = plan(media);
        List<String> cmd = new ArrayList<>(List.of(properties.ffmpegPath(), "-hide_banner", "-nostdin", "-y",
                "-i", input.toString()));

        StringBuilder filter = new StringBuilder("[0:v]split=").append(renditions.size());
        for (int i = 0; i < renditions.size(); i++) {
            filter.append("[s").append(i).append(']');
        }
        for (int i = 0; i < renditions.size(); i++) {
            Size size = renditions.get(i).size();
            filter.append(";[s").append(i).append("]scale=").append(size.width()).append(':').append(size.height())
                    .append(":flags=lanczos,format=yuv420p[v").append(i).append(']');
        }
        cmd.add("-filter_complex");
        cmd.add(filter.toString());

        String preset = properties.effectivePreset();
        for (int i = 0; i < renditions.size(); i++) {
            Rendition rendition = renditions.get(i).rendition();
            cmd.add("-map");
            cmd.add("[v" + i + "]");
            cmd.addAll(properties.encoder().streamOptions(i, preset));
            cmd.addAll(List.of("-b:v:" + i, rendition.bitrate(), "-maxrate:v:" + i, rendition.maxRate(),
                    "-bufsize:v:" + i, rendition.bufferSize()));
        }
        // Align key frames with segment boundaries so every rendition can switch at each segment.
        cmd.addAll(List.of("-force_key_frames", "expr:gte(t,n_forced*" + properties.segmentSeconds() + ")"));
        if (media.hasAudio()) {
            cmd.addAll(List.of("-map", "0:a:0", "-c:a", "aac", "-b:a", "128k", "-ac", "2"));
        }
        cmd.addAll(List.of(
                "-f", "dash",
                "-seg_duration", String.valueOf(properties.segmentSeconds()),
                "-use_template", "1",
                "-use_timeline", "1",
                "-init_seg_name", "init_$RepresentationID$.m4s",
                "-media_seg_name", "chunk_$RepresentationID$_$Number%05d$.m4s",
                "-adaptation_sets", media.hasAudio() ? "id=0,streams=v id=1,streams=a" : "id=0,streams=v",
                "-progress", "pipe:1",
                "-nostats",
                outputDir.resolve("manifest.mpd").toString()));
        return cmd;
    }

    public List<String> thumbnail(Path input, Path output, MediaInfo media) {
        double at = media.duration() > 0 ? Math.min(1.0, media.duration() / 2) : 0;
        Size size = thumbnailSize(media);
        return List.of(properties.ffmpegPath(), "-hide_banner", "-nostdin", "-y",
                "-ss", String.format(Locale.ROOT, "%.3f", at), "-i", input.toString(),
                "-frames:v", "1", "-vf", "scale=" + size.width() + ":" + size.height(), "-q:v", "3",
                output.toString());
    }

    /**
     * Long side at most {@value #THUMBNAIL_LONG_SIDE} pixels (never upscaled), aspect ratio preserved.
     */
    static Size thumbnailSize(MediaInfo media) {
        int sourceLong = Math.max(media.width(), media.height());
        int longSide = evenDown(Math.min(THUMBNAIL_LONG_SIDE, sourceLong));
        int shortSide = nearestEven((double) media.shortSide() * longSide / sourceLong);
        return media.isPortrait() ? new Size(shortSide, longSide) : new Size(longSide, shortSide);
    }

    public List<String> probe(Path input) {
        return List.of(properties.ffprobePath(), "-v", "error", "-print_format", "json", "-show_format",
                "-show_streams", input.toString());
    }

    // H.264 with 4:2:0 chroma needs even dimensions.
    private static int nearestEven(double value) {
        return (int) Math.max(2, 2 * Math.round(value / 2));
    }

    private static int evenDown(int value) {
        return Math.max(2, value - value % 2);
    }

    record Size(int width, int height) {
    }

    record PlannedRendition(Rendition rendition, Size size) {
    }
}
