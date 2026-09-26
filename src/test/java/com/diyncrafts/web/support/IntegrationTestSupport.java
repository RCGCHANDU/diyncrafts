package com.diyncrafts.web.support;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.diyncrafts.web.app.model.Category;
import com.diyncrafts.web.app.model.User;
import com.diyncrafts.web.app.model.Video;
import com.diyncrafts.web.app.repository.es.VideoElasticSearchRepository;
import com.diyncrafts.web.app.repository.jpa.CategoryRepository;
import com.diyncrafts.web.app.repository.jpa.EditorPickRepository;
import com.diyncrafts.web.app.repository.jpa.GuideRepository;
import com.diyncrafts.web.app.repository.jpa.TaskRepository;
import com.diyncrafts.web.app.repository.jpa.UserRepository;
import com.diyncrafts.web.app.repository.jpa.VideoRepository;
import com.diyncrafts.web.app.security.JwtService;
import com.diyncrafts.web.app.service.ThumbnailService;
import com.diyncrafts.web.app.service.VideoS3StorageService;

import software.amazon.awssdk.services.s3.S3AsyncClient;

/**
 * Full application context on an in-memory database with all external services (Elasticsearch,
 * S3, RabbitMQ, ffmpeg) replaced by mocks, so the suite runs without infrastructure or credentials.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestSupport {

    protected static final String PASSWORD = "correct-horse-battery";

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected VideoRepository videoRepository;
    @Autowired
    protected CategoryRepository categoryRepository;
    @Autowired
    protected GuideRepository guideRepository;
    @Autowired
    protected EditorPickRepository editorPickRepository;
    @Autowired
    protected TaskRepository taskRepository;
    @Autowired
    protected PasswordEncoder passwordEncoder;
    @Autowired
    protected JwtService jwtService;

    @MockitoBean
    protected VideoElasticSearchRepository searchRepository;
    @MockitoBean
    protected S3AsyncClient s3AsyncClient;
    @MockitoBean
    protected VideoS3StorageService storageService;
    @MockitoBean
    protected ThumbnailService thumbnailService;
    @MockitoBean
    protected RabbitTemplate rabbitTemplate;

    @BeforeEach
    void cleanDatabase() {
        editorPickRepository.deleteAll();
        guideRepository.deleteAll();
        taskRepository.deleteAll();
        videoRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();
    }

    protected User createUser(String username, User.ERole role) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setEmail(username + "@example.com");
        user.setRole(role);
        user.setEnabled(true);
        return userRepository.save(user);
    }

    protected String tokenFor(User user) {
        return "Bearer " + jwtService.issueToken(user.getUsername(),
                List.of(new SimpleGrantedAuthority(user.getRole().name())));
    }

    protected Category createCategory(String name) {
        Category category = new Category();
        category.setName(name);
        category.setDescription(name + " projects");
        return categoryRepository.save(category);
    }

    protected Video createVideo(User owner, String title) {
        Video video = new Video();
        video.setTitle(title);
        video.setDescription("About " + title);
        video.setThumbnailUrl("https://cdn.example.com/thumb.jpg");
        video.setVideoUrl("https://cdn.example.com/manifest.mpd");
        video.setUploadDate(LocalDate.now());
        video.setViewCount(0L);
        video.setDifficultyLevel("Beginner");
        video.setMaterialsUsed(new ArrayList<>(List.of("pine board")));
        video.setUser(owner);
        return videoRepository.save(video);
    }
}
