package com.diyncrafts.web.app.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.diyncrafts.web.app.config.TranscodingProperties;
import com.diyncrafts.web.app.dto.ChunkedVideoUploadRequest;
import com.diyncrafts.web.app.dto.VideoAndTaskResponse;
import com.diyncrafts.web.app.dto.VideoUploadRequest;
import com.diyncrafts.web.app.exceptions.InvalidRequestException;
import com.diyncrafts.web.app.model.User;

/** Keeps each public-demo request below the forwarded port's body limit. */
@Service
public class ChunkedVideoUploadService {

    public static final int CHUNK_BYTES = 4 * 1024 * 1024;
    private static final long MAX_VIDEO_BYTES = 2L * 1024 * 1024 * 1024;
    private static final Duration SESSION_LIFETIME = Duration.ofHours(2);

    private final Path root;
    private final UserService userService;
    private final VideoUploadService videoUploadService;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public ChunkedVideoUploadService(TranscodingProperties properties, UserService userService,
            VideoUploadService videoUploadService) {
        this.root = properties.workDir().toAbsolutePath().normalize().resolve(".chunked");
        this.userService = userService;
        this.videoUploadService = videoUploadService;
    }

    public void append(UUID uploadId, int index, MultipartFile chunk, Authentication authentication)
            throws IOException {
        User user = userService.currentUser(authentication);
        if (index < 0 || index >= 512 || chunk == null || chunk.isEmpty()) {
            throw new InvalidRequestException("Invalid upload chunk.");
        }
        if (chunk.getSize() > CHUNK_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Upload chunk is too large.");
        }
        if (index == 0) cleanupExpired();
        Session session = sessions.get(uploadId);
        if (session == null && index == 0) {
            Files.createDirectories(root);
            Session fresh = new Session(user.getId(), root.resolve(uploadId.toString()));
            Session existing = sessions.putIfAbsent(uploadId, fresh);
            session = existing == null ? fresh : existing;
        }
        if (session == null) throw new InvalidRequestException("Upload session was not found.");
        synchronized (session) {
            requireOwner(session, user);
            if (session.finished || index != session.nextIndex) {
                throw new InvalidRequestException("Upload chunks arrived out of order.");
            }
            if (session.bytes + chunk.getSize() > MAX_VIDEO_BYTES) {
                throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Video is larger than 2 GB.");
            }
            try (InputStream input = chunk.getInputStream();
                    OutputStream output = Files.newOutputStream(session.path,
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                long written = input.transferTo(output);
                if (written != chunk.getSize()) throw new IOException("Incomplete upload chunk.");
                session.bytes += written;
                session.nextIndex++;
            } catch (IOException | RuntimeException e) {
                sessions.remove(uploadId, session);
                Files.deleteIfExists(session.path);
                throw e;
            }
        }
    }

    public VideoAndTaskResponse complete(UUID uploadId, ChunkedVideoUploadRequest request,
            Authentication authentication) throws IOException {
        User user = userService.currentUser(authentication);
        Session session = sessions.get(uploadId);
        if (session == null) throw new InvalidRequestException("Upload session was not found.");
        synchronized (session) {
            requireOwner(session, user);
            if (session.finished || session.nextIndex != request.chunkCount()
                    || session.bytes != request.totalBytes()) {
                throw new InvalidRequestException("Upload is incomplete. Please try again.");
            }
            session.finished = true;
            sessions.remove(uploadId, session);
        }
        try {
            VideoUploadRequest upload = new VideoUploadRequest(request.title(), request.description(),
                    request.category(), request.difficultyLevel(), request.materialsUsed(),
                    request.thumbnailFile(), new PathMultipartFile(session.path, request.videoFileName(),
                            request.videoContentType()));
            return videoUploadService.upload(upload, authentication);
        } finally {
            Files.deleteIfExists(session.path);
        }
    }

    public void cancel(UUID uploadId, Authentication authentication) throws IOException {
        User user = userService.currentUser(authentication);
        Session session = sessions.get(uploadId);
        if (session == null) return;
        synchronized (session) {
            requireOwner(session, user);
            if (sessions.remove(uploadId, session)) Files.deleteIfExists(session.path);
            session.finished = true;
        }
    }

    private static void requireOwner(Session session, User user) {
        if (!session.ownerId.equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Upload belongs to another user.");
        }
    }

    private void cleanupExpired() throws IOException {
        Instant cutoff = Instant.now().minus(SESSION_LIFETIME);
        for (Map.Entry<UUID, Session> entry : sessions.entrySet()) {
            Session session = entry.getValue();
            if (session.created.isBefore(cutoff)) {
                synchronized (session) {
                    if (sessions.remove(entry.getKey(), session)) Files.deleteIfExists(session.path);
                    session.finished = true;
                }
            }
        }
        if (!Files.isDirectory(root)) return;
        try (Stream<Path> paths = Files.list(root)) {
            for (Path path : paths.toList()) {
                if (Files.isRegularFile(path) && Files.getLastModifiedTime(path).toInstant().isBefore(cutoff)
                        && sessions.values().stream().noneMatch(session -> session.path.equals(path))) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static final class Session {
        final UUID ownerId;
        final Path path;
        final Instant created = Instant.now();
        long bytes;
        int nextIndex;
        boolean finished;

        Session(UUID ownerId, Path path) {
            this.ownerId = ownerId;
            this.path = path;
        }
    }
}

