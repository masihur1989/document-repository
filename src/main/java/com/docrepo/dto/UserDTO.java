package com.docrepo.dto;

import com.docrepo.model.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {

    private String id;
    private String username;
    private String email;
    private Role role;
    private Instant createdAt;
    private Instant updatedAt;
}
