package com.aeroflow.hub.service;

import com.aeroflow.hub.config.BootstrapAdminProperties;
import com.aeroflow.hub.model.Role;
import com.aeroflow.hub.model.User;
import com.aeroflow.hub.repository.RoleRepository;
import com.aeroflow.hub.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class InitialAdminProvisioner implements ApplicationRunner {

    private static final String ADMIN_ROLE = "ROLE_ADMIN";
    private static final int MIN_PASSWORD_LENGTH = 12;
    private static final int MAX_BCRYPT_PASSWORD_BYTES = 72;

    private final BootstrapAdminProperties properties;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (!properties.isEnabled()) {
            log.info("Initial admin provisioning is disabled");
            return;
        }

        if (userRepository.count() > 0) {
            log.info("Initial admin provisioning skipped because users already exist");
            return;
        }

        validateProperties();

        Role adminRole = roleRepository.findByName(ADMIN_ROLE).orElseGet(() -> {
            Role role = new Role();
            role.setName(ADMIN_ROLE);
            return roleRepository.save(role);
        });

        User admin = new User();
        admin.setUsername(properties.getUsername().strip());
        admin.setEmail(properties.getEmail().strip().toLowerCase(Locale.ROOT));
        admin.setPassword(passwordEncoder.encode(properties.getPassword()));
        admin.setEnabled(true);
        admin.setRoles(new HashSet<>(Set.of(adminRole)));
        userRepository.save(admin);

        log.info("Initial administrator provisioned as {}", admin.getUsername());
    }

    private void validateProperties() {
        if (!StringUtils.hasText(properties.getUsername()) || properties.getUsername().length() > 100) {
            throw new IllegalStateException(
                    "BOOTSTRAP_ADMIN_USERNAME is required and must be at most 100 characters.");
        }
        String email = properties.getEmail();
        if (!StringUtils.hasText(email)
                || email.length() > 254
                || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalStateException(
                    "BOOTSTRAP_ADMIN_EMAIL must be a valid email address of at most 254 characters.");
        }
        String password = properties.getPassword();
        int passwordBytes = password == null ? 0 : password.getBytes(StandardCharsets.UTF_8).length;
        if (!StringUtils.hasText(password)
                || password.length() < MIN_PASSWORD_LENGTH
                || passwordBytes > MAX_BCRYPT_PASSWORD_BYTES) {
            throw new IllegalStateException(
                    "BOOTSTRAP_ADMIN_PASSWORD must be at least 12 characters and no more than 72 UTF-8 bytes.");
        }
    }
}
