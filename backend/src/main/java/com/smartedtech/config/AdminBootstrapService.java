package com.smartedtech.config;

import com.smartedtech.user.Role;
import com.smartedtech.user.User;
import com.smartedtech.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class AdminBootstrapService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    @Value("${app.bootstrap-admin.email:}")
    private String adminEmail;

    @Value("${app.bootstrap-admin.password:}")
    private String adminPassword;

    public AdminBootstrapService(UserRepository userRepository, PasswordEncoder passwordEncoder, Environment environment) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!userRepository.existsByRole(Role.ADMIN)) {
            boolean isDev = Arrays.asList(environment.getActiveProfiles()).contains("dev");

            if (isDev) {
                if (adminEmail == null || adminEmail.isBlank()) {
                    adminEmail = "admin@smartedtech.com";
                }
                if (adminPassword == null || adminPassword.isBlank()) {
                    adminPassword = "AdminDevPassword123!";
                }
            } else {
                if (adminEmail == null || adminEmail.isBlank()) {
                    throw new IllegalStateException("Startup failed: APP_BOOTSTRAP_ADMIN_EMAIL must be set in non-dev profiles when no admin exists.");
                }
                if (adminPassword == null || adminPassword.trim().length() < 12) {
                    throw new IllegalStateException("Startup failed: APP_BOOTSTRAP_ADMIN_PASSWORD must be set and at least 12 characters long in non-dev profiles.");
                }
            }

            User admin = new User(
                    adminEmail.trim().toLowerCase(),
                    passwordEncoder.encode(adminPassword.trim()),
                    "System Administrator",
                    Role.ADMIN
            );
            userRepository.save(admin);
            log.info("Bootstrapped initial administrator account: {}", adminEmail.trim().toLowerCase());
        } else {
            log.info("Administrator account already exists. Skipping bootstrap.");
        }
    }
}
