package com.ems.config;

import com.ems.entity.Role;
import com.ems.entity.User;
import com.ems.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Creates the first administrator account so a fresh installation can be signed
 * in to. Runs only when ADMIN_USERNAME and ADMIN_PASSWORD are set and no
 * administrator exists yet. Remove both variables after the first start.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    /** Same rule as the password forms: 10 to 72 characters, a letter and a digit. */
    private static final Pattern PASSWORD_POLICY =
            Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{10,72}$");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${ADMIN_USERNAME:}")
    private String username;

    @Value("${ADMIN_PASSWORD:}")
    private String password;

    @Override
    public void run(ApplicationArguments args) {

        if (username.isBlank() || password.isBlank()) {
            return;
        }

        if (userRepository.existsByRole(Role.ROLE_ADMIN)) {
            log.info("An administrator already exists; ADMIN_USERNAME and "
                    + "ADMIN_PASSWORD are ignored and can be removed.");
            return;
        }

        if (!PASSWORD_POLICY.matcher(password).matches()) {
            log.error("ADMIN_PASSWORD must be 10 to 72 characters and contain "
                    + "a letter and a digit. No administrator was created.");
            return;
        }

        String name = username.trim();

        if (userRepository.existsByUsername(name)) {
            log.error("Username '{}' is already taken by a non-admin account. "
                    + "Choose another ADMIN_USERNAME.", name);
            return;
        }

        User admin = User.builder()
                .username(name)
                .password(passwordEncoder.encode(password))
                .role(Role.ROLE_ADMIN)
                .enabled(true)
                .build();

        userRepository.save(admin);

        log.info("Created initial administrator '{}'. Remove ADMIN_USERNAME and "
                + "ADMIN_PASSWORD from the environment now.", name);
    }
}
