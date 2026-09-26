package com.diyncrafts.web.app.transcoding;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.diyncrafts.web.app.storage.ObjectKeys;

/**
 * Per-task scratch space: {@code {workDir}/{taskId}/input} and {@code {workDir}/{taskId}/output/}.
 */
public final class WorkDirectories {

    private static final Logger log = LoggerFactory.getLogger(WorkDirectories.class);

    private final Path root;

    public WorkDirectories(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public Path taskDir(String taskId) {
        ObjectKeys.videoPrefix(taskId); // validates the id; prevents path traversal
        return root.resolve(taskId);
    }

    public Path input(String taskId) {
        return taskDir(taskId).resolve("input");
    }

    public Path output(String taskId) {
        return taskDir(taskId).resolve("output");
    }

    /**
     * Recursively deletes the task directory; problems are logged, never thrown.
     */
    public void deleteQuietly(String taskId) {
        Path dir = taskDir(taskId);
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    log.warn("Could not delete {}", path, e);
                }
            });
        } catch (IOException e) {
            log.warn("Could not clean up work directory of task {}", taskId, e);
        }
    }

    /**
     * Deletes and recreates the output directory so a retried task starts from a clean state.
     */
    public Path recreateOutput(String taskId) throws IOException {
        Path output = output(taskId);
        if (Files.exists(output)) {
            try (Stream<Path> paths = Files.walk(output)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
        return Files.createDirectories(output);
    }
}
