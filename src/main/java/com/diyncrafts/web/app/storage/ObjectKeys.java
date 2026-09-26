package com.diyncrafts.web.app.storage;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Naming scheme for stored objects. Keys never contain client-supplied file names, so uploads cannot
 * overwrite each other or inject path segments.
 * <ul>
 * <li>{@code thumbnails/{uuid}.{ext}} — uploaded or generated video thumbnails</li>
 * <li>{@code guides/{uuid}.{ext}} — guide images</li>
 * <li>{@code videos/{taskId}/...} — transcoded DASH output (manifest.mpd and segments)</li>
 * </ul>
 */
public final class ObjectKeys {

    private static final Pattern TASK_ID = Pattern.compile("[A-Za-z0-9-]{1,64}");

    private ObjectKeys() {
    }

    public static String thumbnail(ImageType type) {
        return "thumbnails/" + UUID.randomUUID() + type.extension();
    }

    public static String guideImage(ImageType type) {
        return "guides/" + UUID.randomUUID() + type.extension();
    }

    public static String videoPrefix(String taskId) {
        if (taskId == null || !TASK_ID.matcher(taskId).matches()) {
            throw new IllegalArgumentException("Invalid task id");
        }
        return "videos/" + taskId + "/";
    }

    public static String manifest(String taskId) {
        return videoPrefix(taskId) + "manifest.mpd";
    }
}
