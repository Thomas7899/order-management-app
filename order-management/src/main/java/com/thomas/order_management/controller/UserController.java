//src/main/java/com/thomas/order_management/controller/UserController.java
package com.thomas.order_management.controller;

import com.thomas.order_management.dto.auth.AuthDtos;
import com.thomas.order_management.model.Role;
import com.thomas.order_management.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Benutzerverwaltung", description = "Benutzer und Rollen pflegen (nur Administratoren)")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<AuthDtos.UserDto> getAllUsers() {
        return userService.list();
    }

    @Operation(summary = "Verfügbare Rollen")
    @GetMapping("/roles")
    public List<AuthDtos.RoleDto> getRoles() {
        return Arrays.stream(Role.values()).map(AuthDtos.RoleDto::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AuthDtos.UserDto createUser(@Valid @RequestBody AuthDtos.CreateUserRequest request) {
        return userService.create(request);
    }

    @PatchMapping("/{id}")
    public AuthDtos.UserDto updateUser(@PathVariable Long id, @Valid @RequestBody AuthDtos.UpdateUserRequest request,
                                       Authentication authentication) {
        return userService.update(id, request, authentication.getName());
    }
}
