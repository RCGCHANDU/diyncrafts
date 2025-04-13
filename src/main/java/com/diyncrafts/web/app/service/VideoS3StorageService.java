package com.diyncrafts.web.app.service;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.core.sync.RequestBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

@Service
public class VideoS3StorageService {

    private static final Logger logger = LoggerFactory.getLogger(VideoS3StorageService.class);

    private final S3Client s3Client;
    private final String bucketName;
    private final String region;

    public VideoS3StorageService(
        @Value("${aws.s3.bucketName}") String bucketName,
        @Value("${aws.s3.region}") String region
    ) {
        this.bucketName = Objects.requireNonNull(bucketName);
        this.region = Objects.requireNonNull(region);
        this.s3Client = S3Client.builder()
            .region(software.amazon.awssdk.regions.Region.of(region))
            .build();
    }

    public void uploadToS3(String localDirPath, String taskId) {
        validateParameters(localDirPath, taskId);

        File localDir = new File(localDirPath);
        validateDirectory(localDir);

        File[] files = localDir.listFiles();
        if (files == null || files.length == 0) {
            throw new RuntimeException("No transcoded files found in output directory: " + localDirPath);
        }

        for (File file : files) {
            if (file.isFile()) {
                String key = buildKey(taskId, file.getName());
                try (InputStream inputStream = new FileInputStream(file)) {
                    uploadFile(inputStream, file.length(), key);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to upload file: " + file.getName(), e);
                }
            }
        }
    }

    private void validateParameters(String localDirPath, String taskId) {
        if (localDirPath == null || taskId == null) {
            throw new IllegalArgumentException("Local directory path and task ID must be provided");
        }
    }

    private void validateDirectory(File localDir) {
        if (!localDir.exists() || !localDir.isDirectory()) {
            throw new IllegalArgumentException("Output directory does not exist or is not a directory: " + localDir.getAbsolutePath());
        }
    }

    private String buildKey(String taskId, String fileName) {
        return String.format("%s/%s", taskId, fileName);
    }

    public void uploadFile(InputStream inputStream, long contentLength, String key) {
        try {
            PutObjectRequest.Builder putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key);
            // Set Content-Type based on file extension
            if (key.endsWith(".mpd")) {
                putObjectRequest.contentType("application/dash+xml");
            } else if (key.endsWith(".m4s")) {
                // Determine video/audio based on key path
                if (key.contains("/video/")) {
                    putObjectRequest.contentType("video/mp4");
                } else if (key.contains("/audio/")) {
                    putObjectRequest.contentType("audio/mp4");
                }
            }

            RequestBody requestBody = RequestBody.fromInputStream(inputStream, contentLength);

            // Upload the file
            s3Client.putObject(putObjectRequest.build(), requestBody);
            logger.info("Successfully uploaded file to S3: {}", key);
        } catch (S3Exception e) {
            logger.error("AWS Error uploading file: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            logger.error("Error during file upload", e);
            throw new RuntimeException("Error during file upload", e);
        }
    }

    public String getPublicUrl(String taskId) {
        if ("us-east-1".equals(region)) {
            return String.format("https://%s.s3.amazonaws.com/%s/manifest.mpd", bucketName, taskId);
        } else {
            return String.format("https://%s.s3.%s.amazonaws.com/%s/manifest.mpd", bucketName, region, taskId);
        }
    }
}