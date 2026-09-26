package com.diyncrafts.web.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.test.web.servlet.RequestBuilder;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.diyncrafts.web.app.model.Category;
import com.diyncrafts.web.app.model.User;
import com.diyncrafts.web.app.model.User.ERole;
import com.diyncrafts.web.app.model.Video;
import com.diyncrafts.web.app.model.VideoElasticSearch;
import com.diyncrafts.web.app.service.SearchIndexService;
import com.diyncrafts.web.support.IntegrationTestSupport;
import com.jayway.jsonpath.JsonPath;

/**
 * Search behaviour against a real Elasticsearch 9.4 (same minor version as the Java client).
 */
@Testcontainers
class ElasticsearchSearchIT extends IntegrationTestSupport {

    @Container
    @ServiceConnection
    static final ElasticsearchContainer ELASTICSEARCH = new ElasticsearchContainer("elasticsearch:9.4.5")
            .withEnv("xpack.security.enabled", "false")
            .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m");

    @Autowired
    SearchIndexService searchIndexService;
    @Autowired
    ElasticsearchOperations operations;

    Long birdhouse;
    Long shelf;
    Long quilt;

    @BeforeEach
    void seed() {
        if (operations.indexOps(VideoElasticSearch.class).exists()) {
            operations.indexOps(VideoElasticSearch.class).delete();
        }
        User alice = createUser("alice", ERole.ROLE_USER);
        User bob = createUser("bob", ERole.ROLE_USER);
        Category wood = createCategory("Woodworking");
        Category sewing = createCategory("Sewing");
        birdhouse = video(alice, "Cedar birdhouse", "A cosy home for garden birds", wood, "Beginner", "cedar", "nails");
        shelf = video(alice, "Floating shelf", "Hidden bracket wall shelf", wood, "Intermediate", "oak", "screws");
        quilt = video(bob, "Patchwork quilt", "Quilting for beginners", sewing, "Beginner", "cotton");
        assertThat(searchIndexService.reindexAll()).isEqualTo(3);
        operations.indexOps(VideoElasticSearch.class).refresh();
    }

    @Test
    void titleSearchUsesTheQuery() throws Exception {
        assertThat(ids(get("/api/videos/search/title/birdhouse"))).containsExactly(birdhouse);
        assertThat(ids(get("/api/videos/search/title/shelf"))).containsExactly(shelf);
        assertThat(ids(get("/api/videos/search/title/nonexistent"))).isEmpty();
    }

    @Test
    void textSearchIsFuzzyAndRanksTitleMatchesFirst() throws Exception {
        assertThat(ids(get("/api/videos/search/text/birdhose"))).containsExactly(birdhouse);
        assertThat(ids(get("/api/videos/search/text/quilt"))).containsExactly(quilt);
    }

    @Test
    void exactFiltersAreCaseInsensitive() throws Exception {
        assertThat(ids(get("/api/videos/search/category/woodworking"))).containsExactlyInAnyOrder(birdhouse, shelf);
        assertThat(ids(get("/api/videos/search/difficulty/BEGINNER"))).containsExactlyInAnyOrder(birdhouse, quilt);
        assertThat(ids(get("/api/videos/search/user/Bob"))).containsExactly(quilt);
        assertThat(ids(get("/api/videos/search/material/oak"))).containsExactly(shelf);
    }

    @Test
    void combinedFiltersAreAllApplied() throws Exception {
        assertThat(ids(get("/api/videos/search/filters").param("category", "Woodworking")
                .param("difficulty", "Beginner"))).containsExactly(birdhouse);
        assertThat(ids(get("/api/videos/search/filters").param("category", "Woodworking")
                .param("material", "screws"))).containsExactly(shelf);
        assertThat(ids(get("/api/videos/search/advanced").param("searchText", "garden")
                .param("category", "Woodworking").param("minDifficulty", "Beginner"))).containsExactly(birdhouse);
        assertThat(ids(get("/api/videos/search/advanced").param("searchText", "garden")
                .param("category", "Sewing"))).isEmpty();
    }

    @Test
    void emptySearchReturnsNewestFirstWithPaging() throws Exception {
        assertThat(ids(get("/api/videos/search/filters"))).containsExactly(quilt, shelf, birdhouse);
        assertThat(ids(get("/api/videos/search/filters").param("size", "2"))).containsExactly(quilt, shelf);
        assertThat(ids(get("/api/videos/search/filters").param("size", "2").param("page", "1")))
                .containsExactly(birdhouse);
    }

    @Test
    void updatesAndDeletesAreReflected() throws Exception {
        User alice = userRepository.findByUsername("alice").orElseThrow();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .multipart(org.springframework.http.HttpMethod.PUT, "/api/videos/" + shelf)
                .param("title", "Floating bookshelf").param("description", "d").param("difficultyLevel", "Advanced")
                .header("Authorization", tokenFor(alice))).andExpect(status().isOk());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/api/videos/" + birdhouse).header("Authorization", tokenFor(alice)))
                .andExpect(status().isNoContent());
        operations.indexOps(VideoElasticSearch.class).refresh();
        assertThat(ids(get("/api/videos/search/difficulty/advanced"))).containsExactly(shelf);
        assertThat(ids(get("/api/videos/search/title/birdhouse"))).isEmpty();
    }

    @Test
    void adminCanReindex() throws Exception {
        User admin = createUser("root", ERole.ROLE_ADMIN);
        String body = mockMvc.perform(post("/api/admin/search/reindex").header("Authorization", tokenFor(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Integer>read(body, "$.indexed")).isEqualTo(3);
    }

    private Long video(User owner, String title, String description, Category category, String difficulty,
            String... materials) {
        Video video = createVideo(owner, title);
        video.setDescription(description);
        video.setCategory(category);
        video.setDifficultyLevel(difficulty);
        video.setMaterialsUsed(new java.util.ArrayList<>(List.of(materials)));
        return videoRepository.save(video).getId();
    }

    private List<Long> ids(RequestBuilder request) throws Exception {
        String body = mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Number> ids = JsonPath.read(body, "$[*].id");
        return ids.stream().map(Number::longValue).toList();
    }
}
