package com.diyncrafts.web.app.service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.springframework.web.multipart.MultipartFile;

/** Passes an assembled upload into the existing video pipeline without loading it into memory. */
final class PathMultipartFile implements MultipartFile {

    private final Path path;
    private final String filename;
    private final String contentType;
    private final long size;

    PathMultipartFile(Path path, String filename, String contentType) throws IOException {
        this.path = path;
        this.filename = filename;
        this.contentType = contentType;
        this.size = Files.size(path);
    }

    @Override public String getName() { return "videoFile"; }
    @Override public String getOriginalFilename() { return filename; }
    @Override public String getContentType() { return contentType; }
    @Override public boolean isEmpty() { return getSize() == 0; }
    @Override public long getSize() { return size; }
    @Override public byte[] getBytes() throws IOException { return Files.readAllBytes(path); }
    @Override public InputStream getInputStream() throws IOException { return Files.newInputStream(path); }
    @Override public void transferTo(File destination) throws IOException { transferTo(destination.toPath()); }
    @Override public void transferTo(Path destination) throws IOException {
        Files.move(path, destination, StandardCopyOption.REPLACE_EXISTING);
    }
}

