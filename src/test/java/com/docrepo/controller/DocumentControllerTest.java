package com.docrepo.controller;

import com.docrepo.dto.DocumentDTO;
import com.docrepo.dto.DocumentUpdateRequest;
import com.docrepo.model.Document;
import com.docrepo.model.Role;
import com.docrepo.model.User;
import com.docrepo.security.JwtAuthenticationFilter;
import com.docrepo.security.JwtTokenProvider;
import com.docrepo.security.UserPrincipal;
import com.docrepo.service.DocumentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DocumentController.class)
@AutoConfigureMockMvc(addFilters = false)
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DocumentService documentService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private DocumentDTO testDocumentDTO;
    private Document testDocument;
    private UserPrincipal editorPrincipal;

    @BeforeEach
    void setUp() {
        Instant now = Instant.now();
        
        testDocumentDTO = DocumentDTO.builder()
                .id("doc123")
                .filename("test.pdf")
                .originalFilename("test.pdf")
                .contentType("application/pdf")
                .size(1024L)
                .ownerId("user123")
                .ownerUsername("testuser")
                .tags(List.of("tag1", "tag2"))
                .description("Test document")
                .createdAt(now)
                .updatedAt(now)
                .build();

        testDocument = Document.builder()
                .id("doc123")
                .filename("test.pdf")
                .originalFilename("test.pdf")
                .contentType("application/pdf")
                .size(1024L)
                .storageKey("storage-key-123")
                .ownerId("user123")
                .ownerUsername("testuser")
                .tags(List.of("tag1", "tag2"))
                .description("Test document")
                .createdAt(now)
                .updatedAt(now)
                .build();

        User editorUser = User.builder()
                .id("user123")
                .username("testuser")
                .email("editor@test.com")
                .password("password")
                .role(Role.EDITOR)
                .createdAt(now)
                .updatedAt(now)
                .build();
        editorPrincipal = UserPrincipal.create(editorUser);
    }

    @Test
    void listDocuments_Success() throws Exception {
        Page<DocumentDTO> documentPage = new PageImpl<>(List.of(testDocumentDTO));
        when(documentService.listDocuments(any(Pageable.class))).thenReturn(documentPage);

        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("doc123"))
                .andExpect(jsonPath("$.content[0].filename").value("test.pdf"));
    }

    @Test
    void getDocument_Success() throws Exception {
        when(documentService.getDocument("doc123")).thenReturn(testDocumentDTO);

        mockMvc.perform(get("/api/documents/doc123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("doc123"))
                .andExpect(jsonPath("$.filename").value("test.pdf"));
    }

    @Test
    void downloadDocument_Success() throws Exception {
        when(documentService.getDocumentEntity("doc123")).thenReturn(testDocument);
        when(documentService.downloadDocument("doc123"))
                .thenReturn(new ByteArrayInputStream("test content".getBytes()));

        mockMvc.perform(get("/api/documents/doc123/download"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"test.pdf\""))
                .andExpect(header().string("Content-Type", "application/pdf"));
    }


    @Test
    void updateDocument_Success() throws Exception {
        DocumentUpdateRequest updateRequest = DocumentUpdateRequest.builder()
                .filename("updated.pdf")
                .description("Updated description")
                .build();

        DocumentDTO updatedDTO = DocumentDTO.builder()
                .id("doc123")
                .filename("updated.pdf")
                .originalFilename("test.pdf")
                .contentType("application/pdf")
                .size(1024L)
                .ownerId("user123")
                .ownerUsername("testuser")
                .description("Updated description")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(documentService.updateDocument(eq("doc123"), any(DocumentUpdateRequest.class)))
                .thenReturn(updatedDTO);

        mockMvc.perform(put("/api/documents/doc123")
                        .with(user(editorPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filename").value("updated.pdf"));
    }

    @Test
    void deleteDocument_Success() throws Exception {
        doNothing().when(documentService).deleteDocument("doc123");

        mockMvc.perform(delete("/api/documents/doc123")
                        .with(user(editorPrincipal)))
                .andExpect(status().isNoContent());
    }

    @Test
    void listDocumentsByOwner_Success() throws Exception {
        Page<DocumentDTO> documentPage = new PageImpl<>(List.of(testDocumentDTO));
        when(documentService.listDocumentsByOwner(eq("user123"), any(Pageable.class)))
                .thenReturn(documentPage);

        mockMvc.perform(get("/api/documents/owner/user123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].ownerId").value("user123"));
    }

    @Test
    void listDocumentsByTag_Success() throws Exception {
        Page<DocumentDTO> documentPage = new PageImpl<>(List.of(testDocumentDTO));
        when(documentService.listDocumentsByTag(eq("tag1"), any(Pageable.class)))
                .thenReturn(documentPage);

        mockMvc.perform(get("/api/documents/tag/tag1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].tags[0]").value("tag1"));
    }

    @Test
    void searchDocuments_Success() throws Exception {
        Page<DocumentDTO> documentPage = new PageImpl<>(List.of(testDocumentDTO));
        when(documentService.searchDocuments(eq("test"), any(Pageable.class)))
                .thenReturn(documentPage);

        mockMvc.perform(get("/api/documents/search")
                        .param("q", "test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].filename").value("test.pdf"));
    }

}
