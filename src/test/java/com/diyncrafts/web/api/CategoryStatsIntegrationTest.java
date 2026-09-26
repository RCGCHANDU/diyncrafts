package com.diyncrafts.web.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.diyncrafts.web.app.model.Category;
import com.diyncrafts.web.app.model.User;
import com.diyncrafts.web.app.model.User.ERole;
import com.diyncrafts.web.app.model.Video;
import com.diyncrafts.web.app.model.VideoDailyViews;
import com.diyncrafts.web.support.IntegrationTestSupport;

class CategoryStatsIntegrationTest extends IntegrationTestSupport {

    @Test
    void growthComparesViewsInTheLastTwoWindows() throws Exception {
        User user = createUser("alice", ERole.ROLE_USER);
        Category category = createCategory("Woodworking");
        Video video = createVideo(user, "Birdhouse");
        video.setCategory(category);
        // An old video: upload date must not matter, only when views happened.
        video.setUploadDate(LocalDate.now().minusYears(2));
        video.setViewCount(100L);
        videoRepository.save(video);

        // Previous window (16-30 days ago): 4 views.
        dailyViews(video, LocalDate.now().minusDays(20), 4);
        // Outside both windows: ignored for growth.
        dailyViews(video, LocalDate.now().minusDays(45), 50);
        // Recent window: 6 views logged through the API (today).
        for (int i = 0; i < 6; i++) {
            mockMvc.perform(post("/api/videos/" + video.getId() + "/log-view")).andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/categories/stats").header("Authorization", tokenFor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].categoryId").value(category.getId()))
                .andExpect(jsonPath("$[0].viewCount").value(106))
                // (6 - 4) / 4 = +50%
                .andExpect(jsonPath("$[0].growth").value(50.0));
    }

    @Test
    void noViewsMeansZeroGrowth() throws Exception {
        User user = createUser("alice", ERole.ROLE_USER);
        createCategory("Sewing");
        mockMvc.perform(get("/api/categories/stats").header("Authorization", tokenFor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].viewCount").value(0))
                .andExpect(jsonPath("$[0].growth").value(0.0));
    }

    private void dailyViews(Video video, LocalDate day, long views) {
        VideoDailyViews row = new VideoDailyViews();
        row.setId(new VideoDailyViews.Key(video.getId(), day));
        row.setViews(views);
        dailyViewsRepository.save(row);
    }
}
