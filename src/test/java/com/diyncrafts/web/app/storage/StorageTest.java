package com.diyncrafts.web.app.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import com.diyncrafts.web.app.config.StorageProperties;
import com.diyncrafts.web.app.exceptions.InvalidRequestException;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

class StorageTest {

    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F'};
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
    static final byte[] WEBP = {'R', 'I', 'F', 'F', 1, 2, 3, 4, 'W', 'E', 'B', 'P'};

    private final S3Client s3 = mock(S3Client.class);
    private final ObjectStorageService storage = new ObjectStorageService(s3,
            new StorageProperties("bucket", "eu-west-2", null, false, null));

    @Test
    void detectsImagesByContentNotName() {
        assertThat(ImageType.detect(JPEG)).contains(ImageType.JPEG);
        assertThat(ImageType.detect(PNG)).contains(ImageType.PNG);
        assertThat(ImageType.detect(WEBP)).contains(ImageType.WEBP);
        assertThat(ImageType.detect("<html>".getBytes())).isEmpty();
        assertThat(ImageType.detect(new byte[0])).isEmpty();
    }

    @Test
    void keysAreUniqueAndIgnoreClientFileNames() {
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            keys.add(ObjectKeys.thumbnail(ImageType.JPEG));
        }
        assertThat(keys).hasSize(1000).allMatch(key -> key.matches("thumbnails/[0-9a-f-]{36}\\.jpg"));
        assertThat(ObjectKeys.guideImage(ImageType.PNG)).matches("guides/[0-9a-f-]{36}\\.png");
        assertThat(ObjectKeys.manifest("3f2c-abc")).isEqualTo("videos/3f2c-abc/manifest.mpd");
        assertThatThrownBy(() -> ObjectKeys.videoPrefix("../etc")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void storesImageUnderGeneratedKeyWithDetectedContentType() throws Exception {
        var upload = new MockMultipartFile("thumbnailFile", "../../evil name.exe", "text/html", PNG);
        String url = storage.storeImage(upload, ObjectKeys::thumbnail);

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(request.capture(), any(RequestBody.class));
        assertThat(request.getValue().bucket()).isEqualTo("bucket");
        assertThat(request.getValue().key()).matches("thumbnails/[0-9a-f-]{36}\\.png");
        assertThat(request.getValue().contentType()).isEqualTo("image/png");
        assertThat(url).isEqualTo("https://bucket.s3.eu-west-2.amazonaws.com/" + request.getValue().key());
    }

    @Test
    void rejectsNonImages() {
        var upload = new MockMultipartFile("thumbnailFile", "x.jpg", "image/jpeg", "<script>".getBytes());
        assertThatThrownBy(() -> storage.storeImage(upload, ObjectKeys::thumbnail))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void uploadsDashOutputWithContentTypes(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("manifest.mpd"), "<MPD/>");
        Files.write(dir.resolve("init_0.m4s"), new byte[] {1});
        Files.write(dir.resolve("chunk_0_00001.m4s"), new byte[] {2});
        storage.putDirectory("videos/t1/", dir);

        ArgumentCaptor<PutObjectRequest> requests = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3, times(3)).putObject(requests.capture(), any(RequestBody.class));
        assertThat(requests.getAllValues()).extracting(PutObjectRequest::key)
                .containsExactly("videos/t1/chunk_0_00001.m4s", "videos/t1/init_0.m4s", "videos/t1/manifest.mpd");
        assertThat(requests.getAllValues()).extracting(PutObjectRequest::contentType)
                .containsExactly("video/iso.segment", "video/iso.segment", "application/dash+xml");
    }

    @Test
    void publicUrlUsesConfiguredBase() {
        var cdn = new ObjectStorageService(s3, new StorageProperties("b", "r", null, false, "https://cdn.example.com/"));
        assertThat(cdn.publicUrl("videos/t/manifest.mpd")).isEqualTo("https://cdn.example.com/videos/t/manifest.mpd");
    }
}
