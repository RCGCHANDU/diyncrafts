package com.diyncrafts.web.app.transcoding;

import java.util.function.DoubleConsumer;

/**
 * Parses ffmpeg {@code -progress pipe:1} output ({@code out_time_us=...} lines) into a percentage and
 * reports it at most once per whole percent. Completion (100%) is reported by the worker only after
 * the output has been stored, so this tracker caps at 99%.
 */
final class ProgressTracker implements java.util.function.Consumer<String> {

    private final double durationSeconds;
    private final DoubleConsumer listener;
    private int lastReported = -1;

    ProgressTracker(double durationSeconds, DoubleConsumer listener) {
        this.durationSeconds = durationSeconds;
        this.listener = listener;
    }

    @Override
    public void accept(String line) {
        Double seconds = outTimeSeconds(line);
        if (seconds == null || durationSeconds <= 0) {
            return;
        }
        int percent = (int) Math.min(99, Math.max(0, seconds / durationSeconds * 100));
        if (percent > lastReported) {
            lastReported = percent;
            listener.accept(percent);
        }
    }

    static Double outTimeSeconds(String line) {
        // Both keys carry microseconds (out_time_ms is misnamed in ffmpeg).
        String value;
        if (line.startsWith("out_time_us=")) {
            value = line.substring("out_time_us=".length());
        } else if (line.startsWith("out_time_ms=")) {
            value = line.substring("out_time_ms=".length());
        } else {
            return null;
        }
        try {
            return Long.parseLong(value.trim()) / 1_000_000.0;
        } catch (NumberFormatException e) {
            return null; // e.g. "N/A" before the first frame
        }
    }
}
