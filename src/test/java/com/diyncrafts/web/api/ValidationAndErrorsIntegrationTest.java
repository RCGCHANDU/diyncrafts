package com.diyncrafts.web.api;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.diyncrafts.web.app.model.User;
import com.diyncrafts.web.app.model.User.ERole;
import com.diyncrafts.web.support.IntegrationTestSupport;

class ValidationAndErrorsIntegrationTest extends IntegrationTestSupport {

    @Test
    void invalidRegistrationIsRejectedWithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"a\",\"password\":\"short\",\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[*].field", hasItem("username")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("password")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("email")));
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void duplicateRegistrationIsConflict() throws Exception {
        createUser("alice", ERole.ROLE_USER);
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"alice\",\"password\":\"long-enough-pw\",\"email\":\"new@example.com\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"bob\",\"password\":\"long-enough-pw\",\"email\":\"alice@example.com\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void malformedJsonIs400() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void categoryValidationAndConflicts() throws Exception {
        User admin = createUser("root", ERole.ROLE_ADMIN);
        createCategory("Sewing");
        mockMvc.perform(post("/api/categories").header("Authorization", tokenFor(admin))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/categories").header("Authorization", tokenFor(admin))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Sewing\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(put("/api/categories/999999").header("Authorization", tokenFor(admin))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Other\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void categoryInUseCannotBeDeleted() throws Exception {
        User admin = createUser("root", ERole.ROLE_ADMIN);
        var category = createCategory("Woodworking");
        var video = createVideo(admin, "Birdhouse");
        video.setCategory(category);
        videoRepository.save(video);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/api/categories/" + category.getId()).header("Authorization", tokenFor(admin)))
                .andExpect(status().isConflict());
    }

    @Test
    void guideValidation() throws Exception {
        User user = createUser("alice", ERole.ROLE_USER);
        mockMvc.perform(post("/api/guides").header("Authorization", tokenFor(user))
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"T\",\"content\":\"C\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("videoId"));
        mockMvc.perform(post("/api/guides").header("Authorization", tokenFor(user))
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"T\",\"content\":\"C\",\"videoId\":424242}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidPaginationAndIdsAre400() throws Exception {
        mockMvc.perform(get("/api/guides").param("page", "0")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/guides").param("size", "1000")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/guides/not-a-number")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/videos").param("sort", "user.password")).andExpect(status().isBadRequest());
    }

    @Test
    void missingResourcesAre404() throws Exception {
        mockMvc.perform(get("/api/videos/424242")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        mockMvc.perform(get("/api/guides/424242")).andExpect(status().isNotFound());
    }

    @Test
    void videoUploadRequiresMetadataAndFile() throws Exception {
        User user = createUser("alice", ERole.ROLE_USER);
        mockMvc.perform(multipart("/api/videos/upload").param("title", "Birdhouse")
                .header("Authorization", tokenFor(user)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("videoFile")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("description")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("difficultyLevel")));
    }

    @Test
    void unknownCategoryOnVideoUpdateIs400() throws Exception {
        User user = createUser("alice", ERole.ROLE_USER);
        var video = createVideo(user, "Birdhouse");
        mockMvc.perform(multipart(org.springframework.http.HttpMethod.PUT, "/api/videos/" + video.getId())
                .param("title", "T").param("description", "D").param("difficultyLevel", "Beginner")
                .param("category", "Nope").header("Authorization", tokenFor(user)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidProfileEmailIs400() throws Exception {
        User user = createUser("alice", ERole.ROLE_USER);
        mockMvc.perform(put("/api/user/profile").param("mailId", "nope").header("Authorization", tokenFor(user)))
                .andExpect(status().isBadRequest());
    }
}
