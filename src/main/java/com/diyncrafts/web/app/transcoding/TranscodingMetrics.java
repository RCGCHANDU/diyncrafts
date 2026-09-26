package com.diyncrafts.web.app.transcoding;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.diyncrafts.web.app.model.TaskStatus;
import com.diyncrafts.web.app.repository.jpa.TaskRepository;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Transcoding service-level signals: "are uploaded videos actually becoming playable?"
 * <ul>
 * <li>{@code diyncrafts.transcoding.jobs{outcome}}: finished jobs (completed / failed)</li>
 * <li>{@code diyncrafts.transcoding.processing{outcome}}: time from start of processing to the
 * terminal state</li>
 * <li>{@code diyncrafts.transcoding.queue.wait}: time from upload to the start of processing</li>
 * <li>{@code diyncrafts.transcoding.tasks{status}}: tasks currently QUEUED / PROCESSING (from the
 * database, refreshed every 30 s, so every instance reports the same cluster-wide value)</li>
 * </ul>
 */
@Component
public class TranscodingMetrics {

    private static final Logger log = LoggerFactory.getLogger(TranscodingMetrics.class);

    private final MeterRegistry registry;
    private final TaskRepository taskRepository;
    private final Map<TaskStatus, AtomicLong> current = new EnumMap<>(TaskStatus.class);
    private final Timer queueWait;

    public TranscodingMetrics(MeterRegistry registry, TaskRepository taskRepository) {
        this.registry = registry;
        this.taskRepository = taskRepository;
        for (TaskStatus status : new TaskStatus[] {TaskStatus.QUEUED, TaskStatus.PROCESSING}) {
            AtomicLong value = new AtomicLong();
            current.put(status, value);
            Gauge.builder("diyncrafts.transcoding.tasks", value, AtomicLong::get)
                    .description("Transcoding tasks currently in this state")
                    .tag("status", status.name().toLowerCase())
                    .register(registry);
        }
        this.queueWait = Timer.builder("diyncrafts.transcoding.queue.wait")
                .description("Time from upload until a worker starts processing")
                .register(registry);
    }

    void started(Duration waitedInQueue) {
        if (!waitedInQueue.isNegative()) {
            queueWait.record(waitedInQueue);
        }
    }

    void finished(boolean completed, Duration processing) {
        String outcome = completed ? "completed" : "failed";
        registry.counter("diyncrafts.transcoding.jobs", "outcome", outcome).increment();
        if (processing != null && !processing.isNegative()) {
            Timer.builder("diyncrafts.transcoding.processing")
                    .description("Processing time until the task reached a terminal state")
                    .tag("outcome", outcome)
                    .register(registry)
                    .record(processing);
        }
    }

    @Scheduled(fixedDelayString = "PT30S", initialDelayString = "PT10S")
    void refreshTaskCounts() {
        try {
            current.forEach((status, value) -> value.set(taskRepository.countByStatus(status)));
        } catch (RuntimeException e) {
            log.debug("Could not refresh transcoding task counts", e);
        }
    }
}
