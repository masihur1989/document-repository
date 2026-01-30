package com.docrepo.config;

import com.docrepo.model.Role;
import com.docrepo.model.User;
import com.docrepo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile("!test")
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.count() == 0) {
            log.info("Seeding initial users...");
            seedUsers();
            log.info("User seeding completed.");
        } else {
            log.info("Users already exist, skipping seeding.");
        }
    }

    private void seedUsers() {
        List<User> users = List.of(
                createUser("admin", "admin@docrepo.com", "admin123", Role.ADMIN),
                createUser("editor", "editor@docrepo.com", "editor123", Role.EDITOR),
                createUser("viewer", "viewer@docrepo.com", "viewer123", Role.VIEWER)
        );

        userRepository.saveAll(users);

        log.info("Created {} seed users:", users.size());
        users.forEach(user -> log.info("  - {} ({}) with role {}", 
                user.getUsername(), user.getEmail(), user.getRole()));
    }

    private User createUser(String username, String email, String password, Role role) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(role);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        return user;
    }
}
