package com.docrepo.service;

import com.docrepo.dto.AuthRequest;
import com.docrepo.dto.AuthResponse;
import com.docrepo.dto.RegisterRequest;
import com.docrepo.model.Role;
import com.docrepo.model.User;
import com.docrepo.repository.UserRepository;
import com.docrepo.security.JwtTokenProvider;
import com.docrepo.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    private User testUser;
    private final String userId = "user123";
    private final String username = "testuser";
    private final String email = "test@example.com";
    private final String password = "password123";
    private final String encodedPassword = "encodedPassword";
    private final String token = "jwt-token-123";

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(userId);
        testUser.setUsername(username);
        testUser.setEmail(email);
        testUser.setPassword(encodedPassword);
        testUser.setRole(Role.VIEWER);
        testUser.setCreatedAt(Instant.now());
        testUser.setUpdatedAt(Instant.now());
    }

    @Test
    void register_Success() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername(username);
        request.setEmail(email);
        request.setPassword(password);

        when(userRepository.existsByUsername(username)).thenReturn(false);
        when(userRepository.existsByEmail(email)).thenReturn(false);
        when(passwordEncoder.encode(password)).thenReturn(encodedPassword);
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(tokenProvider.generateToken(userId, username, "VIEWER")).thenReturn(token);

        AuthResponse result = authService.register(request);

        assertNotNull(result);
        assertEquals(token, result.getToken());
        assertEquals(userId, result.getUserId());
        assertEquals(username, result.getUsername());
        assertEquals("VIEWER", result.getRole());

        verify(userRepository).existsByUsername(username);
        verify(userRepository).existsByEmail(email);
        verify(passwordEncoder).encode(password);
        verify(userRepository).save(any(User.class));
        verify(tokenProvider).generateToken(userId, username, "VIEWER");
    }

    @Test
    void register_UsernameAlreadyExists() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername(username);
        request.setEmail(email);
        request.setPassword(password);

        when(userRepository.existsByUsername(username)).thenReturn(true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> authService.register(request));

        assertEquals("Username already exists", exception.getMessage());
        verify(userRepository).existsByUsername(username);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void register_EmailAlreadyExists() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername(username);
        request.setEmail(email);
        request.setPassword(password);

        when(userRepository.existsByUsername(username)).thenReturn(false);
        when(userRepository.existsByEmail(email)).thenReturn(true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> authService.register(request));

        assertEquals("Email already exists", exception.getMessage());
        verify(userRepository).existsByUsername(username);
        verify(userRepository).existsByEmail(email);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void login_Success() {
        AuthRequest request = new AuthRequest();
        request.setUsername(username);
        request.setPassword(password);

        UserPrincipal userPrincipal = UserPrincipal.create(testUser);

        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(userPrincipal);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(tokenProvider.generateToken(authentication)).thenReturn(token);

        AuthResponse result = authService.login(request);

        assertNotNull(result);
        assertEquals(token, result.getToken());
        assertEquals(userId, result.getUserId());
        assertEquals(username, result.getUsername());
        assertEquals("VIEWER", result.getRole());

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(tokenProvider).generateToken(authentication);
    }
}
