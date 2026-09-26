package com.diyncrafts.web.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.diyncrafts.web.app.model.Guide;
import com.diyncrafts.web.app.model.Task;
import com.diyncrafts.web.app.model.TaskStatus;
import com.diyncrafts.web.app.model.User;
import com.diyncrafts.web.app.model.User.ERole;
import com.diyncrafts.web.app.model.Video;
import com.diyncrafts.web.support.IntegrationTestSupport;

class OwnershipIntegrationTest extends IntegrationTestSupport {

    @Test
    void userCannotDeleteAnotherUsersVideo() throws Exception {
        User owner = createUser("owner", ERole.ROLE_USER);
        User other = createUser("other", ERole.ROLE_USER);
        Video video = createVideo(owner, "Birdhouse");

        mockMvc.perform(delete("/api/videos/" + video.getId()).header("Authorization", tokenFor(other)))
                .andExpect(status().isForbidden());
        assertThat(videoRepository.existsById(video.getId())).isTrue();
    }

    @Test
    void ownerCanDeleteOwnVideoIncludingItsGuides() throws Exception {
        User owner = createUser("owner", ERole.ROLE_USER);
        Video video = createVideo(owner, "Birdhouse");
        createGuide(owner, video, "Step by step");

        mockMvc.perform(delete("/api/videos/" + video.getId()).header("Authorization", tokenFor(owner)))
                .andExpect(status().isNoContent());
        assertThat(videoRepository.existsById(video.getId())).isFalse();
        assertThat(guideRepository.count()).isZero();
    }

    @Test
    void adminCanDeleteAnyVideo() throws Exception {
        User owner = createUser("owner", ERole.ROLE_USER);
        User admin = createUser("root", ERole.ROLE_ADMIN);
        Video video = createVideo(owner, "Birdhouse");

        mockMvc.perform(delete("/api/videos/" + video.getId()).header("Authorization", tokenFor(admin)))
                .andExpect(status().isNoContent());
        assertThat(videoRepository.existsById(video.getId())).isFalse();
    }

    @Test
    void ownerCanEditMetadataWithoutReuploadingVideo() throws Exception {
        User owner = createUser("owner", ERole.ROLE_USER);
        createCategory("Woodworking");
        Video video = createVideo(owner, "Birdhouse");

        mockMvc.perform(updateVideo(video).header("Authorization", tokenFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Better birdhouse"))
                .andExpect(jsonPath("$.category.name").value("Woodworking"))
                .andExpect(jsonPath("$.videoUrl").value("https://cdn.example.com/manifest.mpd"));
        assertThat(videoRepository.findById(video.getId())).get()
                .extracting(Video::getTitle).isEqualTo("Better birdhouse");
    }

    @Test
    void nonOwnerCannotEditVideo() throws Exception {
        User owner = createUser("owner", ERole.ROLE_USER);
        User other = createUser("other", ERole.ROLE_USER);
        createCategory("Woodworking");
        Video video = createVideo(owner, "Birdhouse");

        mockMvc.perform(updateVideo(video).header("Authorization", tokenFor(other)))
                .andExpect(status().isForbidden());
        assertThat(videoRepository.findById(video.getId())).get()
                .extracting(Video::getTitle).isEqualTo("Birdhouse");
    }

    @Test
    void guideOwnershipIsEnforcedForUpdateAndDelete() throws Exception {
        User owner = createUser("owner", ERole.ROLE_USER);
        User other = createUser("other", ERole.ROLE_USER);
        Guide guide = createGuide(owner, createVideo(owner, "Birdhouse"), "Original");

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/guides/" + guide.getId())
                .param("title", "Hijacked").param("content", "x").header("Authorization", tokenFor(other)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/guides/" + guide.getId()).header("Authorization", tokenFor(other)))
                .andExpect(status().isForbidden());

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/guides/" + guide.getId())
                .param("title", "Updated").param("content", "New content").header("Authorization", tokenFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated"));
        mockMvc.perform(delete("/api/guides/" + guide.getId()).header("Authorization", tokenFor(owner)))
                .andExpect(status().isNoContent());
    }

    @Test
    void taskStatusIsVisibleToVideoOwnerOnlyAndHidesInternalPaths() throws Exception {
        User owner = createUser("owner", ERole.ROLE_USER);
        User other = createUser("other", ERole.ROLE_USER);
        Video video = createVideo(owner, "Birdhouse");
        Task task = new Task();
        task.setTaskId("task-1");
        task.setStatus(TaskStatus.PROCESSING);
        task.setProgress(42.0);
        task.setStartTime(LocalDateTime.now());
        task.setInputPath("/srv/uploads/task-1/input.mp4");
        task.setVideoId(video.getId());
        taskRepository.save(task);

        mockMvc.perform(get("/api/videos/status/task-1").header("Authorization", tokenFor(other)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/videos/status/task-1").header("Authorization", tokenFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.progress").value(42.0))
                .andExpect(content().string(not(containsString("/srv/uploads"))));
        mockMvc.perform(get("/api/videos/status/missing").header("Authorization", tokenFor(owner)))
                .andExpect(status().isNotFound());
    }

    @Test
    void viewCountIncrementsAnonymouslyAndUnknownVideoIs404() throws Exception {
        Video video = createVideo(createUser("owner", ERole.ROLE_USER), "Birdhouse");
        mockMvc.perform(post("/api/videos/" + video.getId() + "/log-view")).andExpect(status().isOk());
        assertThat(videoRepository.findById(video.getId())).get().extracting(Video::getViewCount).isEqualTo(1L);
        mockMvc.perform(post("/api/videos/999999/log-view")).andExpect(status().isNotFound());
    }

    private static MockHttpServletRequestBuilder updateVideo(Video video) {
        return multipart(HttpMethod.PUT, "/api/videos/" + video.getId())
                .param("title", "Better birdhouse")
                .param("description", "Now with a roof")
                .param("category", "Woodworking")
                .param("difficultyLevel", "Intermediate")
                .contentType(MediaType.MULTIPART_FORM_DATA);
    }

    private Guide createGuide(User author, Video video, String title) {
        Guide guide = new Guide();
        guide.setTitle(title);
        guide.setContent("Content of " + title);
        guide.setVideo(video);
        guide.setUser(author);
        return guideRepository.save(guide);
    }
}
