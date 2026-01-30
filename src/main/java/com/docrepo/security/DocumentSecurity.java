package com.docrepo.security;

import com.docrepo.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("documentSecurity")
@RequiredArgsConstructor
public class DocumentSecurity {

    private final DocumentService documentService;

    public boolean isOwner(String documentId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        return documentService.isOwner(documentId, userPrincipal.getId());
    }
}
