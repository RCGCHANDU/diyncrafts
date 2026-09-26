package com.diyncrafts.web.app.transcoding;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

/**
 * Runs ffmpeg/ffprobe as child processes without a shell. Standard output is streamed to a callback
 * (progress), standard error is appended to a per-task log file, and a watchdog kills processes that
 * exceed their time limit.
 */
@Component
public class MediaProcessRunner {

    private static final Logger log = LoggerFactory.getLogger(MediaProcessRunner.class);
    private static final int LOG_TAIL_LINES = 20;

    private final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "media-process-watchdog");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * @throws TranscodingException if the process exits with a non-zero code or times out
     * @throws IOException          if the process cannot be started (e.g. ffmpeg not installed)
     */
    public void run(List<String> command, Path logFile, Duration timeout, Consumer<String> stdoutLines)
            throws IOException, InterruptedException {
        Files.createDirectories(logFile.getParent());
        Process process = new ProcessBuilder(command)
                .redirectError(ProcessBuilder.Redirect.appendTo(logFile.toFile()))
                .start();
        AtomicBoolean timedOut = new AtomicBoolean();
        ScheduledFuture<?> killer = watchdog.schedule(() -> {
            timedOut.set(true);
            process.destroyForcibly();
        }, timeout.toMillis(), TimeUnit.MILLISECONDS);
        try (BufferedReader stdout = process.inputReader(StandardCharsets.UTF_8)) {
            String line;
            while ((line = stdout.readLine()) != null) {
                stdoutLines.accept(line);
            }
            int exitCode = process.waitFor();
            if (timedOut.get()) {
                throw new TranscodingException("Processing took too long and was stopped.");
            }
            if (exitCode != 0) {
                String tail = tail(logFile);
                log.warn("{} exited with code {}; last output:\n{}", command.get(0), exitCode, tail);
                throw new TranscodingException("The video could not be processed.",
                        new IOException(command.get(0) + " exited with code " + exitCode));
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            throw e;
        } finally {
            killer.cancel(false);
        }
    }

    /**
     * Runs a command and returns its complete standard output (used for ffprobe).
     */
    public String capture(List<String> command, Path logFile, Duration timeout)
            throws IOException, InterruptedException {
        StringBuilder out = new StringBuilder();
        run(command, logFile, timeout, line -> out.append(line).append('\n'));
        return out.toString();
    }

    static String tail(Path logFile) {
        try (var lines = Files.lines(logFile, StandardCharsets.UTF_8)) {
            Deque<String> last = new ArrayDeque<>();
            lines.forEach(line -> {
                if (last.size() == LOG_TAIL_LINES) {
                    last.removeFirst();
                }
                last.addLast(line);
            });
            return String.join("\n", last);
        } catch (IOException | java.io.UncheckedIOException e) {
            return "(log unavailable)";
        }
    }

    @PreDestroy
    void shutdown() {
        watchdog.shutdownNow();
    }
}
