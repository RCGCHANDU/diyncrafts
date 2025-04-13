package com.diyncrafts.web.app.service;

import java.util.regex.Matcher;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.diyncrafts.web.app.exceptions.StorageException;

import java.util.regex.Pattern;

import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
public class VideoStorageService {
    private final S3AsyncClient s3Client;
    private final String bucketName;
    private final String region;

    public VideoStorageService(
        S3AsyncClient s3Client,
        @Value("${aws.s3.bucketName}") String bucketName,
        @Value("${aws.s3.region}") String region
    ) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
        this.region = region;
    }

    // Upload file to S3 and return the URL
    public String upload(byte[] bytes, String fileName, String contentType) {
        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(fileName)
                .contentType(contentType)
                .build();

            s3Client.putObject(putRequest, AsyncRequestBody.fromBytes(bytes)).join();
            return generateUrl(fileName); // fileName is the S3 key
        } catch (Exception e) {
            throw new StorageException("Failed to upload to S3", e);
        }
    }

    // Delete an object from S3 by key
    public void delete(String key) {
        DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .build();
        s3Client.deleteObject(deleteRequest).join();
    }

    // Generate a public S3 URL from a key
    public String generateUrl(String key) {
        return String.format("https://%s.s3.%s.amazonaws.com/%s", bucketName, region, key);
    }

    // Extract the S3 key from a URL
    public String extractKeyFromUrl(String url) {
        Pattern pattern = Pattern.compile(".*\\/([^/]+)$");
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new IllegalArgumentException("Invalid S3 URL format");
    }
}