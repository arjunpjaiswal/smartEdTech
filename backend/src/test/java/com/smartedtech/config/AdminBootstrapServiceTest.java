package com.smartedtech.config;

import com.smartedtech.user.Role;
import com.smartedtech.user.User;
import com.smartedtech.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private Environment environment;

    private AdminBootstrapService bootstrapService;

    @BeforeEach
    void setUp() {
        bootstrapService = new AdminBootstrapService(userRepository, passwordEncoder, environment);
    }

    @Test
    @DisplayName("Refuses to start in non-dev profile when no admin exists and email is missing")
    void refusesToStartInNonDevWhenEmailMissing() {
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        ReflectionTestUtils.setField(bootstrapService, "adminEmail", "");
        ReflectionTestUtils.setField(bootstrapService, "adminPassword", "SuperSecurePassword123!");

        assertThatThrownBy(() -> bootstrapService.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_BOOTSTRAP_ADMIN_EMAIL must be set");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Refuses to start in non-dev profile when password is under 12 characters")
    void refusesToStartInNonDevWhenPasswordUnder12Chars() {
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        ReflectionTestUtils.setField(bootstrapService, "adminEmail", "admin@smartedtech.com");
        ReflectionTestUtils.setField(bootstrapService, "adminPassword", "Short123!"); // 9 chars < 12

        assertThatThrownBy(() -> bootstrapService.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 12 characters long");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Allows default fallback in dev profile when no admin exists")
    void allowsDefaultInDevProfile() {
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});
        when(passwordEncoder.encode("AdminDevPassword123!")).thenReturn("encodedDevPass");
        ReflectionTestUtils.setField(bootstrapService, "adminEmail", "");
        ReflectionTestUtils.setField(bootstrapService, "adminPassword", "");

        bootstrapService.run(null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("admin@smartedtech.com");
        assertThat(saved.getPasswordHash()).isEqualTo("encodedDevPass");
        assertThat(saved.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    @DisplayName("Successfully bootstraps admin in non-dev profile when email and >=12 char password are provided")
    void allowsCustomInNonDevWhenPasswordAtLeast12Chars() {
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        when(passwordEncoder.encode("ProductionPassword123!")).thenReturn("encodedProdPass");
        ReflectionTestUtils.setField(bootstrapService, "adminEmail", "prodadmin@smartedtech.com");
        ReflectionTestUtils.setField(bootstrapService, "adminPassword", "ProductionPassword123!");

        bootstrapService.run(null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("prodadmin@smartedtech.com");
        assertThat(saved.getPasswordHash()).isEqualTo("encodedProdPass");
    }

    @Test
    @DisplayName("Skips bootstrap when admin already exists")
    void skipsBootstrapWhenAdminAlreadyExists() {
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(true);

        bootstrapService.run(null);

        verify(userRepository, never()).save(any());
    }
}
