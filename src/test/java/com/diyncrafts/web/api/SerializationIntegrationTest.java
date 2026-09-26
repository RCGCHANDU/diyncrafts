package com.diyncrafts.web.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.diyncrafts.web.app.model.Guide;
import com.diyncrafts.web.app.model.User;
import com.diyncrafts.web.app.model.User.ERole;
import com.diyncrafts.web.app.model.Video;
import com.diyncrafts.web.support.IntegrationTestSupport;

/**
 * Regression tests for the password-hash leak: responses must never contain credentials, other
 * users' emails or security internals.
 */
class SerializationIntegrationTest extends IntegrationTestSupport {

    @Test
    void publicResponsesNeverExposeCredentialsOrEmails() throws Exception {
        User owner = createUser("owner", ERole.ROLE_USER);
        User admin = createUser("root", ERole.ROLE_ADMIN);
        Video video = createVideo(owner, "Birdhouse");
        Guide guide = new Guide();
        guide.setTitle("How to");
        guide.setContent("Steps");
        guide.setVideo(video);
        guide.setUser(owner);
        guide = guideRepository.save(guide);
        mockMvc.perform(post("/api/editor-pick").param("videoId", video.getId().toString())
                .header("Authorization", tokenFor(admin))).andExpect(status().isOk());

        String[] urls = {"/api/videos", "/api/videos/" + video.getId(), "/api/videos/trending",
                "/api/videos/difficulty/Beginner", "/api/guides", "/api/guides/" + guide.getId(),
                "/api/guides/video/" + video.getId(), "/api/editor-pick"};
        for (String url : urls) {
            String body = mockMvc.perform(get(url)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertNoSensitiveData(body, owner, url);
        }

        String mine = mockMvc.perform(get("/api/videos/user").header("Authorization", tokenFor(owner)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertNoSensitiveData(mine, owner, "/api/videos/user");
    }

    @Test
    void videoJsonContainsUploaderSummaryOnly() throws Exception {
        User owner = createUser("owner", ERole.ROLE_USER);
        Video video = createVideo(owner, "Birdhouse");
        mockMvc.perform(get("/api/videos/" + video.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value("owner"))
                .andExpect(jsonPath("$.user.id").value(owner.getId().toString()))
                .andExpect(jsonPath("$.user.password").doesNotExist())
                .andExpect(jsonPath("$.user.email").doesNotExist())
                .andExpect(jsonPath("$.user.authorities").doesNotExist());
    }

    @Test
    void ownProfileIncludesEmailButNoPassword() throws Exception {
        User owner = createUser("owner", ERole.ROLE_USER);
        String body = mockMvc.perform(get("/api/user/profile").header("Authorization", tokenFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("owner@example.com"))
                .andExpect(jsonPath("$.role").value("ROLE_USER"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("password").doesNotContain(owner.getPassword());
    }

    @Test
    void loginResponseContainsNoPasswordHash() throws Exception {
        User owner = createUser("owner", ERole.ROLE_USER);
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"owner\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("password").doesNotContain(owner.getPassword());
    }

    private static void assertNoSensitiveData(String body, User user, String url) {
        assertThat(body).as(url)
                .doesNotContain("password")
                .doesNotContain(user.getPassword())
                .doesNotContain("$2a$")
                .doesNotContain(user.getEmail())
                .doesNotContain("authorities")
                .doesNotContain("inputPath");
    }
}
