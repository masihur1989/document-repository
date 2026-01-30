package com.docrepo.service;

import com.docrepo.dto.AuthRequest;
import com.docrepo.dto.AuthResponse;
import com.docrepo.dto.RegisterRequest;
import com.docrepo.model.Role;
import com.docrepo.model.User;
import com.docrepo.repository.UserRepository;
import com.docrepo.security.JwtTokenProvider;
import com.docrepo.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final AuthenticationManager authenticationManager;

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username already exists");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.VIEWER)
                .build();

        User savedUser = userRepository.save(user);
        log.info("User registered: username={}", savedUser.getUsername());

        String token = tokenProvider.generateToken(
                savedUser.getId(),
                savedUser.getUsername(),
                savedUser.getRole().name()
        );

        return AuthResponse.of(token, savedUser.getId(), savedUser.getUsername(), savedUser.getRole().name());
    }

    public AuthResponse login(AuthRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        String token = tokenProvider.generateToken(authentication);

        log.info("User logged in: username={}", userPrincipal.getUsername());

        return AuthResponse.of(
                token,
                userPrincipal.getId(),
                userPrincipal.getUsername(),
                userPrincipal.getRole().name()
        );
    }
}
