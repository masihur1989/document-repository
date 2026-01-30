package com.docrepo.controller;

import com.docrepo.dto.AuthRequest;
import com.docrepo.dto.AuthResponse;
import com.docrepo.dto.RegisterRequest;
import com.docrepo.security.JwtAuthenticationFilter;
import com.docrepo.security.JwtTokenProvider;
import com.docrepo.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void register_Success() throws Exception {
        RegisterRequest request = new RegisterRequest("testuser", "test@example.com", "password123");

        AuthResponse response = AuthResponse.builder()
                .token("jwt-token-123")
                .tokenType("Bearer")
                .userId("user123")
                .username("testuser")
                .role("VIEWER")
                .build();

        when(authService.register(any(RegisterRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("jwt-token-123"))
                .andExpect(jsonPath("$.username").value("testuser"));
    }

    @Test
    void register_InvalidRequest_MissingUsername() throws Exception {
        RegisterRequest request = new RegisterRequest(null, "test@example.com", "password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_InvalidRequest_InvalidEmail() throws Exception {
        RegisterRequest request = new RegisterRequest("testuser", "invalid-email", "password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_Success() throws Exception {
        AuthRequest request = new AuthRequest("testuser", "password123");

        AuthResponse response = AuthResponse.builder()
                .token("jwt-token-123")
                .tokenType("Bearer")
                .userId("user123")
                .username("testuser")
                .role("VIEWER")
                .build();

        when(authService.login(any(AuthRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token-123"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void login_InvalidRequest_MissingPassword() throws Exception {
        AuthRequest request = new AuthRequest("testuser", null);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
