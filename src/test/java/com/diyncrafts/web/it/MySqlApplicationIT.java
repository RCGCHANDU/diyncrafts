package com.diyncrafts.web.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import com.diyncrafts.web.app.model.User;
import com.diyncrafts.web.app.model.User.ERole;
import com.diyncrafts.web.app.model.Video;
import com.diyncrafts.web.support.IntegrationTestSupport;

/**
 * The application on MySQL with Flyway enabled and {@code ddl-auto=validate}: proves the migrations
 * produce exactly the schema the entities expect, and exercises MySQL-specific SQL.
 */
@Testcontainers
@TestPropertySource(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate"})
class MySqlApplicationIT extends IntegrationTestSupport {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Test
    void schemaValidatesAndUserCanWriteSeveralGuides() throws Exception {
        User author = createUser("author", ERole.ROLE_USER);
        Video video = createVideo(author, "Birdhouse");
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/guides").header("Authorization", tokenFor(author))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"G" + i + "\",\"content\":\"c\",\"videoId\":" + video.getId() + "}"))
                    .andExpect(status().isCreated());
        }
        assertThat(guideRepository.count()).isEqualTo(2);
    }

    @Test
    void viewsAreCountedPerDayWithTheMySqlUpsert() throws Exception {
        User user = createUser("alice", ERole.ROLE_USER);
        var category = createCategory("Woodworking");
        Video video = createVideo(user, "Birdhouse");
        video.setCategory(category);
        videoRepository.save(video);
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/videos/" + video.getId() + "/log-view")).andExpect(status().isOk());
        }
        assertThat(dailyViewsRepository.findAll()).singleElement()
                .satisfies(row -> assertThat(row.getViews()).isEqualTo(3));
        mockMvc.perform(get("/api/categories/stats").header("Authorization", tokenFor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].viewCount").value(3))
                .andExpect(jsonPath("$[0].growth").value(100.0));
    }

    @Test
    void deletingAVideoCascadesToGuidesPicksAndDailyViews() throws Exception {
        User owner = createUser("owner", ERole.ROLE_USER);
        Video video = createVideo(owner, "Birdhouse");
        mockMvc.perform(post("/api/videos/" + video.getId() + "/log-view")).andExpect(status().isOk());
        mockMvc.perform(post("/api/guides").header("Authorization", tokenFor(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"G\",\"content\":\"c\",\"videoId\":" + video.getId() + "}"))
                .andExpect(status().isCreated());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/api/videos/" + video.getId()).header("Authorization", tokenFor(owner)))
                .andExpect(status().isNoContent());
        assertThat(videoRepository.count()).isZero();
        assertThat(guideRepository.count()).isZero();
        assertThat(dailyViewsRepository.count()).isZero();
    }
}
