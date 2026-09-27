package com.diyncrafts.web.app.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import com.diyncrafts.web.app.config.TranscodingProperties;
import com.diyncrafts.web.app.dto.ChunkedVideoUploadRequest;
import com.diyncrafts.web.app.model.User;

class ChunkedVideoUploadServiceTest {

    @TempDir Path scratch;

    @Test
    void assemblesChunksForTheExistingUploadPipeline() throws Exception {
        UserService users = mock(UserService.class);
        VideoUploadService videos = mock(VideoUploadService.class);
        Authentication auth = mock(Authentication.class);
        User owner = new User();
        owner.setId(UUID.randomUUID());
        when(users.currentUser(auth)).thenReturn(owner);
        ChunkedVideoUploadService service = new ChunkedVideoUploadService(properties(), users, videos);
        UUID uploadId = UUID.randomUUID();

        service.append(uploadId, 0, chunk("abc"), auth);
        service.append(uploadId, 1, chunk("def"), auth);
        when(videos.upload(any(), same(auth))).thenAnswer(invocation -> {
            com.diyncrafts.web.app.dto.VideoUploadRequest request = invocation.getArgument(0);
            assertArrayEquals("abcdef".getBytes(StandardCharsets.UTF_8), request.videoFile().getBytes());
            Path moved = scratch.resolve("moved.webm");
            request.videoFile().transferTo(moved);
            assertEquals(6, request.videoFile().getSize());
            assertArrayEquals("abcdef".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(moved));
            return null;
        });

        service.complete(uploadId, request(2, 6), auth);
        verify(videos).upload(any(), same(auth));
        assertFalse(Files.exists(scratch.resolve(".chunked").resolve(uploadId.toString())));
    }

    @Test
    void rejectsAnotherUsersChunksAndOutOfOrderParts() throws Exception {
        UserService users = mock(UserService.class);
        Authentication auth = mock(Authentication.class);
        User owner = new User();
        owner.setId(UUID.randomUUID());
        User stranger = new User();
        stranger.setId(UUID.randomUUID());
        when(users.currentUser(auth)).thenReturn(owner);
        ChunkedVideoUploadService service = new ChunkedVideoUploadService(properties(), users,
                mock(VideoUploadService.class));
        UUID uploadId = UUID.randomUUID();
        service.append(uploadId, 0, chunk("abc"), auth);

        assertThrows(com.diyncrafts.web.app.exceptions.InvalidRequestException.class,
                () -> service.append(uploadId, 2, chunk("def"), auth));
        when(users.currentUser(auth)).thenReturn(stranger);
        assertThrows(ResponseStatusException.class,
                () -> service.append(uploadId, 1, chunk("def"), auth));
    }

    private TranscodingProperties properties() {
        return new TranscodingProperties(scratch, null, null, null, null, 0, null, null);
    }

    private static MockMultipartFile chunk(String text) {
        return new MockMultipartFile("chunk", "part", "application/octet-stream",
                text.getBytes(StandardCharsets.UTF_8));
    }

    private static ChunkedVideoUploadRequest request(int chunks, long bytes) {
        return new ChunkedVideoUploadRequest("Title", "Description", "Cooking", "Beginner",
                List.of("pan"), null, "video.webm", "video/webm", chunks, bytes);
    }
}

