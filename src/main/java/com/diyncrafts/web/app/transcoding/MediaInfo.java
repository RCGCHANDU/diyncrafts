package com.diyncrafts.web.app.transcoding;

/**
 * What ffprobe reported about an input file.
 *
 * @param width    display width (after applying rotation metadata)
 * @param height   display height (after applying rotation metadata)
 * @param duration seconds; 0 when unknown
 */
public record MediaInfo(int width, int height, double duration, boolean hasAudio) {

    public int shortSide() {
        return Math.min(width, height);
    }

    public boolean isPortrait() {
        return height > width;
    }
}
