package com.diyncrafts.web.deploy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.diyncrafts.web.app.model.User;
import com.diyncrafts.web.app.model.User.ERole;
import com.diyncrafts.web.support.IntegrationTestSupport;

/**
 * Uploads are refused before anything is stored when the scratch disk cannot hold the transcoded
 * output plus the configured reserve, instead of failing later with "No space left on device".
 */
@TestPropertySource(properties = "app.transcoding.min-free-space=1000000TB")
class ScratchSpaceGuardTest extends IntegrationTestSupport {

    @MockitoBean
    RabbitTemplate rabbitTemplate;

    @Test
    void uploadIsRejectedWith503WhenScratchSpaceIsLow() throws Exception {
        User user = createUser("alice", ERole.ROLE_USER);
        mockMvc.perform(multipart("/api/videos/upload")
                        .file(new MockMultipartFile("videoFile", "clip.mp4", "video/mp4", new byte[] {1, 2, 3}))
                        .param("title", "Birdhouse").param("description", "A small birdhouse")
                        .param("difficultyLevel", "Beginner")
                        .header("Authorization", tokenFor(user)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("Video processing storage is temporarily full. Please try again later."));

        assertThat(taskRepository.count()).isZero();
        assertThat(videoRepository.count()).isZero();
        verify(rabbitTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }
}
