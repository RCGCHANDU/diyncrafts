package com.diyncrafts.web.app.transcoding;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.diyncrafts.web.app.config.RabbitConfig;
import com.diyncrafts.web.app.config.TranscodingProperties;
import com.diyncrafts.web.app.model.TaskStatus;
import com.diyncrafts.web.app.model.VideoElasticSearch;
import com.diyncrafts.web.app.service.SearchIndexService;
import com.diyncrafts.web.app.storage.ImageType;
import com.diyncrafts.web.app.storage.ObjectKeys;
import com.diyncrafts.web.app.storage.ObjectStorageService;
import com.diyncrafts.web.app.transcoding.TranscodingTaskService.TaskSnapshot;

/**
 * Consumes transcoding jobs: probe → DASH transcode → optional thumbnail → upload → complete.
 * <p>
 * Failure policy:
 * <ul>
 * <li>{@link TranscodingException} (bad input, ffmpeg error, timeout) is permanent: the task is marked
 * FAILED right away and the message is acknowledged.</li>
 * <li>Anything else (storage, database, missing ffmpeg) propagates; the listener container retries a
 * few times and then {@link TranscodingFailureRecoverer} fails the task and dead-letters the message.</li>
 * </ul>
 * The task directory is deleted once the task is COMPLETED or FAILED; it is kept between retries
 * because the retry needs the uploaded input.
 */
@Component
public class TranscodingWorker {

    private static final Logger log = LoggerFactory.getLogger(TranscodingWorker.class);
    private static final Duration PROBE_TIMEOUT = Duration.ofMinutes(2);

    private final TranscodingTaskService tasks;
    private final MediaProcessRunner runner;
    private final FfmpegCommandBuilder commands;
    private final ObjectStorageService storage;
    private final SearchIndexService searchIndex;
    private final ProgressNotifier notifier;
    private final WorkDirectories workDirectories;
    private final TranscodingProperties properties;

    public TranscodingWorker(TranscodingTaskService tasks, MediaProcessRunner runner, ObjectStorageService storage,
            SearchIndexService searchIndex, ProgressNotifier notifier, WorkDirectories workDirectories,
            TranscodingProperties properties) {
        this.tasks = tasks;
        this.runner = runner;
        this.commands = new FfmpegCommandBuilder(properties);
        this.storage = storage;
        this.searchIndex = searchIndex;
        this.notifier = notifier;
        this.workDirectories = workDirectories;
        this.properties = properties;
    }

    @RabbitListener(queues = RabbitConfig.TRANSCODING_QUEUE)
    public void onJob(TranscodingJob job) throws InterruptedException {
        MDC.put("taskId", job.taskId());
        try {
            Optional<TaskSnapshot> snapshot = tasks.start(job.taskId(), job.videoId());
            if (snapshot.isPresent()) {
                process(snapshot.get());
            }
        } finally {
            MDC.remove("taskId");
        }
    }

    private void process(TaskSnapshot task) throws InterruptedException {
        String taskId = task.taskId();
        Instant started = Instant.now();
        log.info("Transcoding started: task {} video {} encoder {}", taskId, task.videoId(),
                properties.encoder().ffmpegName());
        try {
            Path input = task.inputPath() == null ? null : Path.of(task.inputPath());
            if (input == null || !Files.isReadable(input)) {
                throw new TranscodingException("The uploaded file is no longer available. Please upload it again.");
            }
            Path logFile = workDirectories.taskDir(taskId).resolve("ffmpeg.log");
            MediaInfo media = FfprobeParser.parse(runner.capture(commands.probe(input), logFile, PROBE_TIMEOUT));
            Path output = workDirectories.recreateOutput(taskId);

            runner.run(commands.dash(input, output, media), logFile, properties.timeout(),
                    new ProgressTracker(media.duration(), percent -> reportProgress(taskId, percent)));

            String thumbnailUrl = tasks.needsThumbnail(task.videoId())
                    ? generateThumbnail(taskId, input, media, logFile)
                    : null;
            storage.putDirectory(ObjectKeys.videoPrefix(taskId), output);
            String manifestUrl = storage.publicUrl(ObjectKeys.manifest(taskId));
            Optional<VideoElasticSearch> document = tasks.complete(taskId, task.videoId(), manifestUrl, thumbnailUrl);
            document.ifPresent(searchIndex::save);
            notifier.publish(taskId, 100, TaskStatus.COMPLETED);
            log.info("Transcoding completed: task {} in {}s ({}x{}, audio={})", taskId,
                    Duration.between(started, Instant.now()).toSeconds(), media.width(), media.height(),
                    media.hasAudio());
            workDirectories.deleteQuietly(taskId);
        } catch (TranscodingException e) {
            log.warn("Transcoding failed: task {}: {}", taskId, e.getMessage(), e);
            tasks.fail(taskId, e.getMessage());
            notifier.publish(taskId, 0, TaskStatus.FAILED);
            workDirectories.deleteQuietly(taskId);
        } catch (IOException e) {
            // Infrastructure problem (disk, missing ffmpeg): let the container retry.
            throw new UncheckedIOException("Transcoding infrastructure failure for task " + taskId, e);
        }
    }

    private String generateThumbnail(String taskId, Path input, MediaInfo media, Path logFile)
            throws IOException, InterruptedException {
        Path thumbnail = workDirectories.taskDir(taskId).resolve("thumbnail.jpg");
        runner.run(commands.thumbnail(input, thumbnail, media), logFile, PROBE_TIMEOUT, line -> { });
        String key = ObjectKeys.thumbnail(ImageType.JPEG);
        storage.putFile(key, thumbnail, ImageType.JPEG.contentType());
        return storage.publicUrl(key);
    }

    private void reportProgress(String taskId, double percent) {
        try {
            tasks.updateProgress(taskId, percent);
        } catch (RuntimeException e) {
            log.debug("Could not persist progress for task {}", taskId, e);
        }
        notifier.publish(taskId, percent, TaskStatus.PROCESSING);
    }
}
