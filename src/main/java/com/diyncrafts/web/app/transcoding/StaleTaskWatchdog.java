package com.diyncrafts.web.app.transcoding;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.diyncrafts.web.app.config.TranscodingProperties;

/**
 * Fails tasks left in PROCESSING well past the transcoding timeout, e.g. after a worker crash where
 * the broker exhausted its delivery limit. Their inputs are removed as well.
 */
@Component
public class StaleTaskWatchdog {

    private static final Logger log = LoggerFactory.getLogger(StaleTaskWatchdog.class);

    private final TranscodingTaskService tasks;
    private final TranscodingProperties properties;
    private final Clock clock;

    private final WorkDirectories workDirectories;

    public StaleTaskWatchdog(TranscodingTaskService tasks, WorkDirectories workDirectories,
            TranscodingProperties properties, Clock clock) {
        this.tasks = tasks;
        this.workDirectories = workDirectories;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "PT10M", initialDelayString = "PT5M")
    public void failStaleTasks() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minus(properties.timeout().multipliedBy(2));
        List<String> failed = tasks.failStale(cutoff);
        failed.forEach(workDirectories::deleteQuietly);
        if (!failed.isEmpty()) {
            log.warn("Marked stale transcoding task(s) as FAILED: {}", failed);
        }
    }
}
