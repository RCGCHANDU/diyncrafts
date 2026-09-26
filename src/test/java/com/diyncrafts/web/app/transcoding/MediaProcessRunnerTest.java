package com.diyncrafts.web.app.transcoding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

@DisabledOnOs(OS.WINDOWS)
class MediaProcessRunnerTest {

    @TempDir
    Path dir;

    private final MediaProcessRunner runner = new MediaProcessRunner();

    @Test
    void streamsStdoutAndWritesStderrToLog() throws Exception {
        List<String> lines = new ArrayList<>();
        runner.run(List.of("sh", "-c", "echo one; echo two; echo oops >&2"), dir.resolve("log"),
                Duration.ofSeconds(10), lines::add);
        assertThat(lines).containsExactly("one", "two");
        assertThat(MediaProcessRunner.tail(dir.resolve("log"))).isEqualTo("oops");
    }

    @Test
    void nonZeroExitIsAProcessingFailure() {
        assertThatThrownBy(() -> runner.run(List.of("sh", "-c", "exit 3"), dir.resolve("log"),
                Duration.ofSeconds(10), line -> { }))
                .isInstanceOf(TranscodingException.class)
                .hasMessage("The video could not be processed.");
    }

    @Test
    void processIsKilledWhenTheCallbackFails() throws Exception {
        Path marker = dir.resolve("still-running");
        assertThatThrownBy(() -> runner.run(List.of("sh", "-c", "echo tick; sleep 2; touch " + marker),
                dir.resolve("log"), Duration.ofMinutes(1), line -> {
                    throw new IllegalStateException("callback failed");
                }))
                .isInstanceOf(IllegalStateException.class);
        // A leaked child would create the marker after 2 s.
        Thread.sleep(3000);
        assertThat(marker).doesNotExist();
    }

    @Test
    void timeoutKillsTheProcess() {
        assertThatThrownBy(() -> runner.run(List.of("sh", "-c", "exec sleep 30"), dir.resolve("log"),
                Duration.ofMillis(200), line -> { }))
                .isInstanceOf(TranscodingException.class)
                .hasMessage("Processing took too long and was stopped.");
    }
}
