package com.diyncrafts.web.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.diyncrafts.web.app.model.User;
import com.diyncrafts.web.app.model.User.ERole;
import com.diyncrafts.web.support.IntegrationTestSupport;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

class SecurityIntegrationTest extends IntegrationTestSupport {

    @Autowired
    JwtDecoder jwtDecoder;
    @Autowired
    JwtEncoder jwtEncoder;

    // --- 401 for anonymous requests to protected endpoints (previously NullPointerException / 500)

    @Test
    void anonymousRequestsToProtectedEndpointsReturn401() throws Exception {
        mockMvc.perform(get("/api/videos/user")).andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mockMvc.perform(get("/api/guides/user")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/videos/status/some-task")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/user/profile")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/videos/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Pottery\"}")).andExpect(status().isUnauthorized());
        assertThat(categoryRepository.existsByName("Pottery")).isFalse();
    }

    @Test
    void publicEndpointsAreReachableAnonymously() throws Exception {
        mockMvc.perform(get("/api/categories")).andExpect(status().isOk());
        mockMvc.perform(get("/api/videos")).andExpect(status().isOk());
        mockMvc.perform(get("/api/guides")).andExpect(status().isOk());
        mockMvc.perform(get("/api/editor-pick")).andExpect(status().isNoContent());
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    // --- admin-only operations

    @Test
    void normalUserCannotPerformAdminOperations() throws Exception {
        User user = createUser("alice", ERole.ROLE_USER);
        var category = createCategory("Sewing");
        var video = createVideo(user, "Birdhouse");

        mockMvc.perform(post("/api/categories").header("Authorization", tokenFor(user))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Pottery\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/categories/" + category.getId()).header("Authorization", tokenFor(user))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Hacked\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/categories/" + category.getId()).header("Authorization", tokenFor(user)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/editor-pick").param("videoId", video.getId().toString())
                .header("Authorization", tokenFor(user)))
                .andExpect(status().isForbidden());

        assertThat(categoryRepository.existsByName("Pottery")).isFalse();
        assertThat(categoryRepository.findById(category.getId())).get()
                .extracting(c -> c.getName()).isEqualTo("Sewing");
        assertThat(editorPickRepository.count()).isZero();
    }

    @Test
    void adminCanPerformAdminOperations() throws Exception {
        User admin = createUser("root", ERole.ROLE_ADMIN);
        var video = createVideo(admin, "Birdhouse");

        mockMvc.perform(post("/api/categories").header("Authorization", tokenFor(admin))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Pottery\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Pottery"));
        mockMvc.perform(post("/api/editor-pick").param("videoId", video.getId().toString())
                .header("Authorization", tokenFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.video.id").value(video.getId()));
    }

    // --- registration cannot grant privileges

    @Test
    void registrationIgnoresClientSuppliedRoleAndCreatesRegularUser() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"username":"mallory","password":"super-secret-pw","email":"m@example.com","role":"ROLE_ADMIN"}
                """)).andExpect(status().isOk());

        User created = userRepository.findByUsername("mallory").orElseThrow();
        assertThat(created.getRole()).isEqualTo(ERole.ROLE_USER);
        assertThat(created.getPassword()).isNotEqualTo("super-secret-pw").startsWith("$2");

        mockMvc.perform(post("/api/auth/admin/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"mallory\",\"password\":\"super-secret-pw\"}"))
                .andExpect(status().isForbidden());
    }

    // --- JWT carries and restores authorities

    @Test
    void adminLoginIssuesTokenWhoseAuthoritiesAreEnforced() throws Exception {
        createUser("root", ERole.ROLE_ADMIN);
        String body = mockMvc.perform(post("/api/auth/admin/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"root\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ROLE_ADMIN"))
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.token");

        Jwt jwt = jwtDecoder.decode(token);
        assertThat(jwt.getSubject()).isEqualTo("root");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ROLE_ADMIN");

        mockMvc.perform(post("/api/categories").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Pottery\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void userLoginTokenCarriesUserRoleOnly() throws Exception {
        createUser("alice", ERole.ROLE_USER);
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"alice\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.token");
        assertThat(jwtDecoder.decode(token).getClaimAsStringList("roles")).containsExactly("ROLE_USER");

        mockMvc.perform(get("/api/user/profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"));
    }

    @Test
    void loginFailuresReturnGeneric401() throws Exception {
        createUser("alice", ERole.ROLE_USER);
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"alice\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid username or password."));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"nobody\",\"password\":\"whatever-pw\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid username or password."));
    }

    @Test
    void forgedExpiredOrForeignTokensAreRejected() throws Exception {
        createUser("root", ERole.ROLE_ADMIN);
        Instant now = Instant.now();

        JwtEncoder attackerEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(
                "attacker-controlled-secret-0123456789abcdef".getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        String forged = encode(attackerEncoder, "diyncrafts", now.plusSeconds(600));
        String expired = encode(jwtEncoder, "diyncrafts", now.minusSeconds(600));
        String wrongIssuer = encode(jwtEncoder, "someone-else", now.plusSeconds(600));

        for (String token : List.of(forged, expired, wrongIssuer, "not-a-jwt")) {
            mockMvc.perform(get("/api/user/profile").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().string(containsString("\"status\":401")));
        }
        // Sanity check: a correctly signed, current token from the same issuer works.
        mockMvc.perform(get("/api/user/profile")
                .header("Authorization", "Bearer " + encode(jwtEncoder, "diyncrafts", now.plusSeconds(600))))
                .andExpect(status().isOk());
    }

    private static String encode(JwtEncoder encoder, String issuer, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject("root")
                .issuedAt(expiresAt.minusSeconds(3600))
                .expiresAt(expiresAt)
                .claim("roles", List.of("ROLE_ADMIN"))
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
