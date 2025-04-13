package com.diyncrafts.web.app.service;


import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.diyncrafts.web.app.model.ProgressMessage;
import com.diyncrafts.web.app.model.Task;
import com.diyncrafts.web.app.model.TaskStatus;
import com.diyncrafts.web.app.model.Video;
import com.diyncrafts.web.app.repository.jpa.TaskRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class VideoTranscodingService {
    private static final Logger logger = LoggerFactory.getLogger(VideoTranscodingService.class);

    @Autowired
    private VideoS3StorageService storageService;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Value("${file.output.path}")
    private String outputBasePath;

    // Constants for FFmpeg configuration
    private static final String[] FFPROBE_DURATION_COMMAND = {"ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0"};
    private static final String FFmpeg_BINARY = "ffmpeg";
    private static final String[] DASH_OUTPUT_OPTIONS = {
        "-f", "dash",
        "-seg_duration", "7",
        "-use_template", "1",
        "-init_seg_name", "\"init_$RepresentationID$.m4s\"",
        "-media_seg_name", "\"chunk_$RepresentationID$_$Number$.m4s\"",
        "-adaptation_sets", "\"id=0,streams=v id=1,streams=a\""
    };
    private static final String[] DEFAULT_AUDIO_STREAM = {
        "-map", "0:a", "-c:a", "aac", "-b:a", "128k"
    };

    // Video stream configurations
    private static final List<StreamConfig> VIDEO_STREAMS = Arrays.asList(
        new StreamConfig(0, "6M", "8M", "12M"),
        new StreamConfig(1, "4M", "5M", "10M"),
        new StreamConfig(2, "2M", "3M", "6M"),
        new StreamConfig(3, "1M", "2M", "4M")
    );

    @RabbitListener(queues = "transcoding.queue")
    @Transactional
    public void processTask(String taskId) {
        Task task = null;
        try {
            task = retrieveAndStartTask(taskId);
            validateInputFile(task.getInputPath());
            String outputDir = setupOutputDirectory(task);
            double totalDuration = getDuration(task.getInputPath());
            List<String> command = buildFFmpegCommand(task.getInputPath(), outputDir);
            Process ffmpegProcess = executeFFmpegCommand(command);
            setupProgressTracking(ffmpegProcess, task, totalDuration);
            int exitCode = ffmpegProcess.waitFor();
            handleProcessCompletion(task, exitCode, outputDir);
        } catch (Exception e) {
            handleException(task, e);
        } finally {
            updateEndTime(task);
        }
    }

    // Helper methods...

    private Task retrieveAndStartTask(String taskId) {
        Task task = taskRepository.findById(taskId).orElseThrow();
        task.setStatus(TaskStatus.PROCESSING);
        task.setStartTime(LocalDateTime.now());
        taskRepository.save(task);
        return task;
    }

    private void validateInputFile(String inputPath) {
        File inputFile = new File(inputPath);
        if (!inputFile.exists() || !inputFile.canRead()) {
            throw new RuntimeException("Input file not found or unreadable: " + inputPath);
        }
    }

    private String setupOutputDirectory(Task task) {
        // Use the configured outputBasePath if task's outputLocation is null
        String baseOutput = task.getOutputLocation() != null 
            ? task.getOutputLocation() 
            : outputBasePath; // Fallback to @Value-configured path
        
        String outputDirPath = baseOutput + '/' + task.getTaskId();
        File dir = new File(outputDirPath);
        if (!dir.mkdirs() && !dir.isDirectory()) {
            throw new RuntimeException("Failed to create output directory: " + outputDirPath);
        }
        return outputDirPath;
    }

    private List<String> buildFFmpegCommand(String inputPath, String outputDir) {
        List<String> command = new ArrayList<>();
        command.add(FFmpeg_BINARY);
        command.add("-i");
        command.add("\"" + inputPath + "\"");

        // Build filter_complex
        List<String> scaleFilters = new ArrayList<>();
        for (int i = 0; i < VIDEO_STREAMS.size(); i++) {
            scaleFilters.add(String.format(
                "[v%d]scale=%d:%d:flags=lanczos,settb=AVTB,setpts=PTS-STARTPTS[v%d]",
                i, // Input: [v0], [v1], etc.
                getResolutionWidth(i), getResolutionHeight(i), i
            ));
        }

        // Build the split command with semicolons
        String splitCommand = "[0:v]split=" + scaleFilters.size() + "[v0][v1][v2][v3]; ";
        String filterComplex = splitCommand + String.join("; ", scaleFilters); // Add semicolons between filters

        command.add("-filter_complex");
        command.add("\"" + filterComplex + "\"");

        // Add video streams
        for (StreamConfig config : VIDEO_STREAMS) {
            addVideoStreamCommand(command, config);
        }

        // Add audio stream
        command.addAll(Arrays.asList(DEFAULT_AUDIO_STREAM));

        // Add DASH output options
        command.addAll(Arrays.asList(DASH_OUTPUT_OPTIONS));

        // Output path
        command.add("\"" + outputDir + "/manifest.mpd" + "\"");

        logger.info("FFmpeg command: " + String.join(" ", command));

        return command;
    }

    private void addVideoStreamCommand(List<String> command, StreamConfig config) {
        int index = config.index;
        command.add("-map");
        command.add("\"[v" + index + "]\"");
        command.add("-c:v:" + index);
        command.add("h264_nvenc");
        command.add("-b:v:" + index);
        command.add(config.bitrate);
        command.add("-maxrate");
        command.add(config.maxrate);
        command.add("-bufsize");
        command.add(config.bufsize);
        command.add("-preset");
        command.add("hq");
        command.add("-tune");
        command.add("hq");
        command.add("-bf");
        command.add("0");
    }

    private int getResolutionWidth(int index) {
        return switch (index) {
            case 0 -> 1920;
            case 1 -> 1280;
            case 2 -> 854;
            case 3 -> 640;
            default -> throw new IllegalArgumentException("Invalid stream index");
        };
    }

    private int getResolutionHeight(int index) {
        return switch (index) {
            case 0 -> 1080;
            case 1 -> 720;
            case 2 -> 480;
            case 3 -> 360;
            default -> throw new IllegalArgumentException("Invalid stream index");
        };
    }

    private Process executeFFmpegCommand(List<String> command) throws IOException {
        return new ProcessBuilder(command).start();
    }

    private void setupProgressTracking(Process process, Task task, double totalDuration) {
        Thread progressThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getErrorStream()))) {
                
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.debug("FFmpeg output: {}", line); // Log all lines
                    
                    if (line.contains("time=")) {
                        double progress = calculateProgress(line, totalDuration);
                        task.setProgress(progress);
                        taskRepository.save(task);
                        messagingTemplate.convertAndSend(
                            "/topic/progress-" + task.getTaskId(), 
                            new ProgressMessage(task.getTaskId(), progress)
                        );
                    }
                }
            } catch (IOException e) {
                handleException(task, "Progress tracking failed", e);
            }
        });
        progressThread.start();
    }
    

    private double calculateProgress(String line, double totalDuration) {
        try {
            int timeIndex = line.indexOf("time=");
            if (timeIndex == -1) return 0.0;
    
            // Extract substring starting at "time=" up to the next space
            String timeStr = line.substring(timeIndex + 5).split(" ")[0];
    
            // Skip if timeStr is "N/A" or malformed
            if (timeStr.equals("N/A")) return 0.0;
    
            // Split the time part into hours, minutes, seconds
            String[] timeParts = timeStr.split(":");
            if (timeParts.length != 3) return 0.0; // If the time format is not valid, return 0.0
    
            double hours = Double.parseDouble(timeParts[0]);
            double minutes = Double.parseDouble(timeParts[1]);
            double seconds = Double.parseDouble(timeParts[2]);
    
            // Convert the time to total seconds
            double totalSeconds = hours * 3600 + minutes * 60 + seconds;
    
            // If totalDuration is invalid (<= 0), don't attempt to calculate progress
            if (totalDuration <= 0) return 0.0;
    
            // Calculate progress percentage
            return (totalSeconds / totalDuration) * 100.0;
    
        } catch (Exception e) {
            // Log the exception and return 0.0 if there's a parsing error
            logger.warn("Failed to calculate progress from line: '{}', error: {}", line, e.getMessage());
            return 0.0;
        }
    }
    

    private void handleProcessCompletion(Task task, int exitCode, String outputDir) {
        if (exitCode == 0) {
            task.setStatus(TaskStatus.COMPLETED);
            task.setProgress(100.0);
            // Upload to S3
            storageService.uploadToS3(outputDir, task.getTaskId());

            Video video = new Video();
            video.setVideoUrl(storageService.getPublicUrl(task.getTaskId()));

            // Update output location to S3 URL
            task.setOutputLocation(storageService.getPublicUrl(task.getTaskId()));
        } else {
            task.setErrorDetails("FFmpeg exited with code " + exitCode);
            task.setStatus(TaskStatus.FAILED);
        }
        taskRepository.save(task);
    }

    private void handleException(Task task, Exception e) {
        handleException(task, "Transcoding failed", e);
    }

    private void handleException(Task task, String message, Exception e) {
        logger.error("{}", message, e);
        task.setErrorDetails(e.getMessage());
        task.setStatus(TaskStatus.FAILED);
        taskRepository.save(task);
    }

    private void updateEndTime(Task task) {
        task.setEndTime(LocalDateTime.now());
        taskRepository.save(task);
    }

    private double getDuration(String inputPath) {
        // Correctly build the ffprobe command with the input file as the last argument
        List<String> command = new ArrayList<>(Arrays.asList(FFPROBE_DURATION_COMMAND));
        command.add(inputPath); // Add the input file path as the last argument
    
        try {
            Process process = new ProcessBuilder(command)
                .redirectErrorStream(true) // Merge error stream into input stream
                .start();
    
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
                String durationStr = reader.readLine().trim();
                return Double.parseDouble(durationStr);
            }
        } catch (IOException | NumberFormatException e) {
            throw new RuntimeException("Failed to get video duration", e);
        }
    }

    // Inner class for stream configurations
    private static class StreamConfig {
        final int index;
        final String bitrate;
        final String maxrate;
        final String bufsize;

        StreamConfig(int index, String bitrate, String maxrate, String bufsize) {
            this.index = index;
            this.bitrate = bitrate;
            this.maxrate = maxrate;
            this.bufsize = bufsize;
        }
    }
}