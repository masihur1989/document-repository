package com.docrepo.controller;

import com.docrepo.dto.UserDTO;
import com.docrepo.model.Role;
import com.docrepo.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User management endpoints (Admin only)")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    @Operation(summary = "List all users", description = "Get paginated list of all users. Requires ADMIN role.")
    @ApiResponse(responseCode = "200", description = "Users retrieved successfully")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<UserDTO>> listUsers(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<UserDTO> users = userService.listUsers(pageable);
        return ResponseEntity.ok(users);
    }

    @Operation(summary = "Get user by ID", description = "Get user details by ID. Requires ADMIN role.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserDTO> getUser(
            @Parameter(description = "User ID") @PathVariable String id) {
        UserDTO user = userService.getUser(id);
        return ResponseEntity.ok(user);
    }

    @Operation(summary = "Update user role", description = "Update a user's role. Requires ADMIN role.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User role updated successfully"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @PutMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserDTO> updateUserRole(
            @Parameter(description = "User ID") @PathVariable String id,
            @Parameter(description = "New role to assign") @RequestParam Role role) {
        log.info("Update user role request: userId={}, newRole={}", id, role);
        UserDTO user = userService.updateUserRole(id, role);
        return ResponseEntity.ok(user);
    }

    @Operation(summary = "Delete user", description = "Delete a user account. Requires ADMIN role.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "User deleted successfully"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteUser(
            @Parameter(description = "User ID") @PathVariable String id) {
        log.info("Delete user request: userId={}", id);
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
