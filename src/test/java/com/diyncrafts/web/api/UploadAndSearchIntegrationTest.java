package com.diyncrafts.web.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;

import com.diyncrafts.web.app.config.RabbitConfig;
import com.diyncrafts.web.app.model.Task;
import com.diyncrafts.web.app.model.TaskStatus;
import com.diyncrafts.web.app.model.User;
import com.diyncrafts.web.app.model.User.ERole;
import com.diyncrafts.web.app.model.Video;
import com.diyncrafts.web.app.transcoding.TranscodingJob;
import com.diyncrafts.web.support.IntegrationTestSupport;
import com.jayway.jsonpath.JsonPath;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

class UploadAndSearchIntegrationTest extends IntegrationTestSupport {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};

    @Value("${app.transcoding.work-dir}")
    Path workDir;

    @Test
    void uploadCreatesVideoAndQueuedTaskAndPublishesJob() throws Exception {
        User user = createUser("alice", ERole.ROLE_USER);
        createCategory("Woodworking");
        String body = mockMvc.perform(multipart("/api/videos/upload")
                        .file(new MockMultipartFile("videoFile", "clip.mp4", "video/mp4", new byte[] {1, 2, 3}))
                        .file(new MockMultipartFile("thumbnailFile", "thumb.png", "image/png", PNG))
                        .param("title", "Birdhouse").param("description", "A small birdhouse")
                        .param("difficultyLevel", "Beginner").param("category", "Woodworking")
                        .param("materialsUsed", "pine", "nails")
                        .header("Authorization", tokenFor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.video.title").value("Birdhouse"))
                .andExpect(jsonPath("$.video.videoUrl").doesNotExist())
                .andExpect(jsonPath("$.video.thumbnailUrl").value(org.hamcrest.Matchers.matchesPattern(
                        "https://cdn\\.test/thumbnails/[0-9a-f-]{36}\\.png")))
                .andExpect(jsonPath("$.video.materialsUsed[1]").value("nails"))
                .andExpect(jsonPath("$.task.status").value("QUEUED"))
                .andReturn().getResponse().getContentAsString();

        String taskId = JsonPath.read(body, "$.task.taskId");
        Long videoId = ((Number) JsonPath.read(body, "$.video.id")).longValue();
        verify(rabbitTemplate).convertAndSend(RabbitConfig.TRANSCODING_QUEUE, new TranscodingJob(taskId, videoId));
        Task task = taskRepository.findById(taskId).orElseThrow();
        assertThat(task.getVideoId()).isEqualTo(videoId);
        assertThat(Path.of(task.getInputPath())).exists().hasBinaryContent(new byte[] {1, 2, 3});
        assertThat(videoRepository.findById(videoId)).get().extracting(Video::getVideoUrl).isNull();

        ArgumentCaptor<PutObjectRequest> put = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(put.capture(), any(RequestBody.class));
        assertThat(put.getValue().contentType()).isEqualTo("image/png");
    }

    @Test
    void legacyCreateEndpointRunsTheSamePipeline() throws Exception {
        User user = createUser("alice", ERole.ROLE_USER);
        mockMvc.perform(multipart("/api/videos/create/")
                        .file(new MockMultipartFile("videoFile", "clip.mp4", "video/mp4", new byte[] {1}))
                        .param("title", "Birdhouse").param("description", "d").param("difficultyLevel", "Beginner")
                        .header("Authorization", tokenFor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Birdhouse"));
        verify(rabbitTemplate).convertAndSend(eq(RabbitConfig.TRANSCODING_QUEUE), any(TranscodingJob.class));
    }

    @Test
    void brokerOutageFailsTaskCleansUpAndReturns503() throws Exception {
        User user = createUser("alice", ERole.ROLE_USER);
        doThrow(new AmqpConnectException(new java.net.ConnectException("refused")))
                .when(rabbitTemplate).convertAndSend(eq(RabbitConfig.TRANSCODING_QUEUE), any(TranscodingJob.class));

        mockMvc.perform(multipart("/api/videos/upload")
                        .file(new MockMultipartFile("videoFile", "clip.mp4", "video/mp4", new byte[] {1}))
                        .param("title", "Birdhouse").param("description", "d").param("difficultyLevel", "Beginner")
                        .header("Authorization", tokenFor(user)))
                .andExpect(status().isServiceUnavailable());

        Task task = taskRepository.findAll().get(0);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(task.getErrorDetails()).doesNotContain(workDir.toString());
        assertThat(workDir.resolve(task.getTaskId())).doesNotExist();
    }

    @Test
    void invalidThumbnailOrCategoryIsRejectedBeforeAnythingIsStored() throws Exception {
        User user = createUser("alice", ERole.ROLE_USER);
        mockMvc.perform(multipart("/api/videos/upload")
                        .file(new MockMultipartFile("videoFile", "clip.mp4", "video/mp4", new byte[] {1}))
                        .file(new MockMultipartFile("thumbnailFile", "t.png", "image/png", "<svg/>".getBytes()))
                        .param("title", "T").param("description", "d").param("difficultyLevel", "Beginner")
                        .header("Authorization", tokenFor(user)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(multipart("/api/videos/upload")
                        .file(new MockMultipartFile("videoFile", "clip.mp4", "video/mp4", new byte[] {1}))
                        .param("title", "T").param("description", "d").param("difficultyLevel", "Beginner")
                        .param("category", "Nope")
                        .header("Authorization", tokenFor(user)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(multipart("/api/videos/upload")
                        .file(new MockMultipartFile("videoFile", "clip.mp4", "video/mp4", new byte[0]))
                        .param("title", "T").param("description", "d").param("difficultyLevel", "Beginner")
                        .header("Authorization", tokenFor(user)))
                .andExpect(status().isBadRequest());
        assertThat(videoRepository.count()).isZero();
        assertThat(taskRepository.count()).isZero();
        verify(rabbitTemplate, never()).convertAndSend(anyString(), any(Object.class));
        verifyNoInteractions(s3Client);
    }

    @Test
    void ownerCanReplaceThumbnailOnEdit() throws Exception {
        User user = createUser("alice", ERole.ROLE_USER);
        Video video = createVideo(user, "Birdhouse");
        mockMvc.perform(multipart(HttpMethod.PUT, "/api/videos/" + video.getId())
                        .file(new MockMultipartFile("thumbnailFile", "t.png", "image/png", PNG))
                        .param("title", "T").param("description", "d").param("difficultyLevel", "Beginner")
                        .header("Authorization", tokenFor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.thumbnailUrl").value(org.hamcrest.Matchers.startsWith("https://cdn.test/thumbnails/")))
                .andExpect(jsonPath("$.videoUrl").value("https://cdn.example.com/manifest.mpd"));
    }

    @Test
    void searchReturns503WhenElasticsearchIsUnavailable() throws Exception {
        mockMvc.perform(get("/api/videos/search/text/birdhouse"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("A backing service is temporarily unavailable."));
    }

    @Test
    void searchParametersAreValidated() throws Exception {
        mockMvc.perform(get("/api/videos/search/text/x").param("size", "1000")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/videos/search/filters").param("page", "-1")).andExpect(status().isBadRequest());
    }

    @Test
    void reindexIsAdminOnly() throws Exception {
        User user = createUser("alice", ERole.ROLE_USER);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/admin/search/reindex").header("Authorization", tokenFor(user)))
                .andExpect(status().isForbidden());
    }
}
