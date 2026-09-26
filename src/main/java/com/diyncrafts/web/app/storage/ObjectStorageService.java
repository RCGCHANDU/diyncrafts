package com.diyncrafts.web.app.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.diyncrafts.web.app.config.StorageProperties;
import com.diyncrafts.web.app.exceptions.InvalidRequestException;
import com.diyncrafts.web.app.exceptions.StorageException;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Stores objects in the configured bucket. Keys (see {@link ObjectKeys}), content types and public
 * URLs are kept separate: callers persist the URL returned by {@link #publicUrl(String)}.
 */
@Service
public class ObjectStorageService {

    private static final Logger log = LoggerFactory.getLogger(ObjectStorageService.class);
    private static final int IMAGE_HEADER_BYTES = 16;

    private final S3Client s3Client;
    private final StorageProperties properties;

    public ObjectStorageService(S3Client s3Client, StorageProperties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
    }

    /**
     * Validates an uploaded image by its content and stores it under a fresh key.
     *
     * @param keyFactory {@link ObjectKeys#thumbnail} or {@link ObjectKeys#guideImage}
     * @return the public URL of the stored image
     */
    public String storeImage(MultipartFile image, Function<ImageType, String> keyFactory)
            throws IOException {
        ImageType type = detectImageType(image);
        String key = keyFactory.apply(type);
        try (InputStream in = image.getInputStream()) {
            put(key, RequestBody.fromInputStream(in, image.getSize()), type.contentType());
        }
        return publicUrl(key);
    }

    public void putFile(String key, Path file, String contentType) {
        put(key, RequestBody.fromFile(file), contentType);
    }

    /**
     * Uploads every regular file below {@code directory} under {@code prefix}, keeping relative paths.
     */
    public void putDirectory(String prefix, Path directory) throws IOException {
        List<Path> files;
        try (Stream<Path> walk = Files.walk(directory)) {
            files = walk.filter(Files::isRegularFile).sorted().toList();
        }
        if (files.isEmpty()) {
            throw new StorageException("No files to upload in " + directory.getFileName(), null);
        }
        for (Path file : files) {
            String relative = directory.relativize(file).toString().replace('\\', '/');
            putFile(prefix + relative, file, contentTypeFor(relative));
        }
        log.info("Uploaded {} objects under {}", files.size(), prefix);
    }

    public String publicUrl(String key) {
        return properties.resolvedPublicBaseUrl() + "/" + key;
    }

    static String contentTypeFor(String fileName) {
        String name = fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".mpd")) {
            return "application/dash+xml";
        }
        if (name.endsWith(".m4s")) {
            return "video/iso.segment";
        }
        if (name.endsWith(".mp4")) {
            return "video/mp4";
        }
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        return "application/octet-stream";
    }

    private static ImageType detectImageType(MultipartFile image) throws IOException {
        byte[] header;
        try (InputStream in = image.getInputStream()) {
            header = in.readNBytes(IMAGE_HEADER_BYTES);
        }
        return ImageType.detect(header)
                .orElseThrow(() -> new InvalidRequestException("Images must be JPEG, PNG or WebP."));
    }

    private void put(String key, RequestBody body, String contentType) {
        try {
            s3Client.putObject(PutObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(key)
                    .contentType(contentType)
                    .build(), body);
            log.debug("Stored object {}", key);
        } catch (SdkException e) {
            throw new StorageException("Failed to store object " + key, e);
        }
    }
}
