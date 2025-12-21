package ch.purbank.core.config;

import ch.purbank.core.domain.User;
import ch.purbank.core.domain.enums.Role;
import ch.purbank.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class StartupConfig implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        createDefaultAdminUser();
    }

    private void createDefaultAdminUser() {
        String adminEmail = "admin@purbank.ch";

        if (userRepository.findByEmail(adminEmail).isPresent()) {
            log.info("Default admin user already exists, skipping creation");
            return;
        }

        log.info("Creating default admin user...");

        User admin = User.builder()
                .email(adminEmail)
                .firstName("Admin")
                .lastName("User")
                .contractNumber("10000000") // Fixed contract number for admin
                .password(passwordEncoder.encode("admin123"))
                .role(Role.ADMIN)
                .status("ACTIVE")
                .build();

        userRepository.save(admin);
        log.info("Default admin user created successfully with email: {} and password: admin123", adminEmail);
    }
}