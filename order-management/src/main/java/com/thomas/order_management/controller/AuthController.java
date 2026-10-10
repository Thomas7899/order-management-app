package com.thomas.order_management.controller;

import com.thomas.order_management.dto.auth.AuthDtos;
import com.thomas.order_management.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Anmeldung", description = "Anmeldung per Benutzer/Kennwort, liefert ein JWT")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Anmelden")
    @PostMapping("/login")
    public AuthDtos.LoginResponse login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        return userService.login(request);
    }

    @Operation(summary = "Angemeldeter Benutzer")
    @GetMapping("/me")
    public AuthDtos.UserDto me(Authentication authentication) {
        return userService.me(authentication.getName());
    }

    @Operation(summary = "Demo-Zugänge für die Anmeldeseite")
    @GetMapping("/demo-users")
    public List<AuthDtos.DemoUserDto> demoUsers() {
        return userService.demoUsers();
    }
}
