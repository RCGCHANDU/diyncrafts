package com.diyncrafts.web.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.diyncrafts.web.app.model.User;
import com.diyncrafts.web.app.model.User.ERole;
import com.diyncrafts.web.app.model.Video;
import com.diyncrafts.web.support.IntegrationTestSupport;
import com.jayway.jsonpath.JsonPath;

class GuideIntegrationTest extends IntegrationTestSupport {

    @Test
    void oneUserCanCreateManyGuides() throws Exception {
        User author = createUser("author", ERole.ROLE_USER);
        Video video = createVideo(author, "Birdhouse");
        for (int i = 1; i <= 3; i++) {
            mockMvc.perform(post("/api/guides").header("Authorization", tokenFor(author))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"Guide " + i + "\",\"content\":\"Body\",\"videoId\":" + video.getId() + "}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.user.username").value("author"))
                    .andExpect(jsonPath("$.video.id").value(video.getId()));
        }
        mockMvc.perform(get("/api/guides/user").header("Authorization", tokenFor(author)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void paginationReturnsDifferentPages() throws Exception {
        User author = createUser("author", ERole.ROLE_USER);
        Video video = createVideo(author, "Birdhouse");
        for (int i = 1; i <= 5; i++) {
            mockMvc.perform(post("/api/guides").header("Authorization", tokenFor(author))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"Guide " + i + "\",\"content\":\"Body\",\"videoId\":" + video.getId() + "}"))
                    .andExpect(status().isCreated());
        }
        List<String> page1 = titles(get("/api/guides").param("page", "1").param("size", "2"));
        List<String> page2 = titles(get("/api/guides").param("page", "2").param("size", "2"));
        List<String> page3 = titles(get("/api/guides").param("page", "3").param("size", "2"));
        assertThat(page1).containsExactly("Guide 1", "Guide 2");
        assertThat(page2).containsExactly("Guide 3", "Guide 4");
        assertThat(page3).containsExactly("Guide 5");

        assertThat(titles(get("/api/guides/video/" + video.getId()).param("page", "2").param("size", "3")))
                .containsExactly("Guide 4", "Guide 5");
    }

    private List<String> titles(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
        String body = mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$[*].title");
    }
}
