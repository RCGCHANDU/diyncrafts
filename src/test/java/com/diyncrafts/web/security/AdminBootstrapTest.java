package com.diyncrafts.web.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.diyncrafts.web.app.model.User;
import com.diyncrafts.web.app.repository.jpa.UserRepository;
import com.diyncrafts.web.app.security.AdminBootstrap;

class AdminBootstrapTest {

    private final UserRepository repository = mock(UserRepository.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Test
    void createsAdminWhenMissing() {
        when(repository.findByUsername("root")).thenReturn(Optional.empty());
        new AdminBootstrap(repository, encoder, "root", "a-long-admin-password", "root@example.com").run(null);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(User.ERole.ROLE_ADMIN);
        assertThat(encoder.matches("a-long-admin-password", saved.getValue().getPassword())).isTrue();
    }

    @Test
    void neverPromotesAnExistingRegularUser() {
        User squatter = new User();
        squatter.setUsername("root");
        squatter.setRole(User.ERole.ROLE_USER);
        when(repository.findByUsername("root")).thenReturn(Optional.of(squatter));

        new AdminBootstrap(repository, encoder, "root", "a-long-admin-password", "root@example.com").run(null);

        assertThat(squatter.getRole()).isEqualTo(User.ERole.ROLE_USER);
        verify(repository, never()).save(any());
    }

    @Test
    void doesNothingWhenNotConfiguredAndRejectsWeakPasswords() {
        new AdminBootstrap(repository, encoder, "", "", "").run(null);
        verify(repository, never()).findByUsername(any());

        assertThatThrownBy(() -> new AdminBootstrap(repository, encoder, "root", "short", "r@example.com").run(null))
                .isInstanceOf(IllegalStateException.class);
    }
}
