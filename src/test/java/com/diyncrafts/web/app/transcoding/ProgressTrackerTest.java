package com.diyncrafts.web.app.transcoding;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class ProgressTrackerTest {

    @Test
    void reportsEachWholePercentOnceAndCapsAt99() {
        List<Double> reported = new ArrayList<>();
        ProgressTracker tracker = new ProgressTracker(10.0, reported::add);
        for (String line : List.of("frame=1", "out_time_us=N/A", "out_time_us=1000000", "out_time_ms=1050000",
                "out_time_us=5000000", "progress=continue", "out_time_us=12000000", "progress=end")) {
            tracker.accept(line);
        }
        assertThat(reported).containsExactly(10.0, 50.0, 99.0);
    }

    @Test
    void unknownDurationReportsNothing() {
        List<Double> reported = new ArrayList<>();
        new ProgressTracker(0, reported::add).accept("out_time_us=1000000");
        assertThat(reported).isEmpty();
    }
}
