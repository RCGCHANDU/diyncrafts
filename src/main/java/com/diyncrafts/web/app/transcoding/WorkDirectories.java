package com.diyncrafts.web.app.transcoding;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.diyncrafts.web.app.config.TranscodingProperties;

import com.diyncrafts.web.app.storage.ObjectKeys;

/**
 * Per-task scratch space: {@code {workDir}/{taskId}/input} and {@code {workDir}/{taskId}/output/}.
 * Multipart uploads are buffered in {@code {workDir}/.multipart} (see {@code spring.servlet.multipart.location})
 * so that moving an upload into its task directory is a rename on the same filesystem, not a copy.
 */
@Component
public class WorkDirectories {

    private static final Logger log = LoggerFactory.getLogger(WorkDirectories.class);

    private final Path root;

    static final String MULTIPART_DIR = ".multipart";

    public WorkDirectories(TranscodingProperties properties) {
        this.root = properties.workDir().toAbsolutePath().normalize();
        try {
            Files.createDirectories(root.resolve(MULTIPART_DIR));
        } catch (IOException e) {
            log.warn("Could not create work directory {}; uploads and transcoding will fail until it exists", root, e);
        }
    }

    /**
     * Bytes available to this process on the scratch filesystem.
     */
    public long usableSpace() throws IOException {
        Files.createDirectories(root);
        return Files.getFileStore(root).getUsableSpace();
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
