package com.thomas.order_management.dto.auth;

import com.thomas.order_management.model.Role;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank(message = "Benutzer ist erforderlich") String username,
            @NotBlank(message = "Kennwort ist erforderlich") String password
    ) {
    }

    public record LoginResponse(String token, Instant expiresAt, UserDto user) {
    }

    public record RoleDto(Role code, String text, String description) {
        public static RoleDto of(Role role) {
            return new RoleDto(role, role.getDisplayName(), role.getDescription());
        }
    }

    public record UserDto(
            Long id,
            String username,
            String fullName,
            String email,
            List<RoleDto> roles,
            boolean active,
            LocalDateTime lastLoginAt,
            LocalDateTime createdAt
    ) {
    }

    /** Demo-Zugang für die Anmeldeseite (nur Benutzer und Rollen, kein Kennwort). */
    public record DemoUserDto(String username, String fullName, List<RoleDto> roles) {
    }

    public record CreateUserRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9]{3,12}$", message = "Benutzer: 3–12 Buchstaben oder Ziffern") String username,
            @NotBlank @Size(max = 100) String fullName,
            @NotBlank @Email String email,
            @NotEmpty(message = "Mindestens eine Rolle ist erforderlich") Set<Role> roles,
            @NotBlank @Size(min = 8, message = "Kennwort: mindestens 8 Zeichen") String password
    ) {
    }

    public record UpdateUserRequest(
            @Size(max = 100) String fullName,
            @Email String email,
            Set<Role> roles,
            Boolean active,
            @Size(min = 8, message = "Kennwort: mindestens 8 Zeichen") String password
    ) {
    }
}
