package com.thomas.order_management.service;

import com.thomas.order_management.dto.auth.AuthDtos;
import com.thomas.order_management.exception.BusinessException;
import com.thomas.order_management.exception.ConflictException;
import com.thomas.order_management.exception.ResourceNotFoundException;
import com.thomas.order_management.model.Role;
import com.thomas.order_management.model.User;
import com.thomas.order_management.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Anmeldung und Benutzerverwaltung (angelehnt an SU01).
 */
@Service
@Transactional
public class UserService {

    /** Kennwort aller Demo-Benutzer; wird auf der Anmeldeseite angezeigt. */
    public static final String DEMO_PASSWORD = "demo1234";

    private static final Duration TOKEN_VALIDITY = Duration.ofHours(8);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final boolean demoUsersEnabled;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder,
                       @Value("${app.demo.users-enabled:true}") boolean demoUsersEnabled) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.demoUsersEnabled = demoUsersEnabled;
    }

    public AuthDtos.LoginResponse login(AuthDtos.LoginRequest request) {
        User user = userRepository.findByUsernameIgnoreCase(request.username().trim())
                .filter(User::isActive)
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new BusinessException("00", "152",
                        "Benutzer oder Kennwort ist falsch", HttpStatus.UNAUTHORIZED));

        user.setLastLoginAt(LocalDateTime.now());
        Instant now = Instant.now();
        Instant expiresAt = now.plus(TOKEN_VALIDITY);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("order-management")
                .subject(user.getUsername())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("name", user.getFullName())
                .claim("roles", user.getRoles().stream().map(Role::name).sorted().toList())
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new AuthDtos.LoginResponse(token, expiresAt, toDto(user));
    }

    @Transactional(readOnly = true)
    public AuthDtos.UserDto me(String username) {
        return userRepository.findByUsernameIgnoreCase(username).map(this::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Benutzer " + username + " nicht gefunden"));
    }

    @Transactional(readOnly = true)
    public List<AuthDtos.DemoUserDto> demoUsers() {
        if (!demoUsersEnabled) {
            return List.of();
        }
        return userRepository.findAllByOrderByUsernameAsc().stream()
                .filter(u -> u.isActive() && u.isDemoAccount())
                .map(u -> new AuthDtos.DemoUserDto(u.getUsername(), u.getFullName(), roles(u)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AuthDtos.UserDto> list() {
        return userRepository.findAllByOrderByUsernameAsc().stream().map(this::toDto).toList();
    }

    public AuthDtos.UserDto create(AuthDtos.CreateUserRequest request) {
        String username = request.username().toUpperCase(Locale.ROOT);
        userRepository.findByUsernameIgnoreCase(username).ifPresent(u -> {
            throw new ConflictException("Benutzer " + username + " existiert bereits");
        });
        userRepository.findByEmail(request.email()).ifPresent(u -> {
            throw new ConflictException("E-Mail " + request.email() + " wird bereits verwendet");
        });
        User user = new User(username, request.fullName(), request.email(),
                passwordEncoder.encode(request.password()), request.roles());
        return toDto(userRepository.save(user));
    }

    public AuthDtos.UserDto update(Long id, AuthDtos.UpdateUserRequest request, String currentUsername) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Benutzer " + id + " nicht gefunden"));
        boolean self = user.getUsername().equalsIgnoreCase(currentUsername);
        if (self && (Boolean.FALSE.equals(request.active())
                || (request.roles() != null && !request.roles().contains(Role.ADMIN)))) {
            throw new BusinessException("01", "496", "Sie können sich nicht selbst sperren oder die Administratorrolle entziehen");
        }
        if (request.fullName() != null) user.setFullName(request.fullName());
        if (request.email() != null && !request.email().equalsIgnoreCase(user.getEmail())) {
            userRepository.findByEmail(request.email()).ifPresent(u -> {
                throw new ConflictException("E-Mail " + request.email() + " wird bereits verwendet");
            });
            user.setEmail(request.email());
        }
        if (request.roles() != null && !request.roles().isEmpty()) user.setRoles(EnumSet.copyOf(request.roles()));
        if (request.active() != null) user.setActive(request.active());
        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
            user.setDemoAccount(false);
        }
        return toDto(userRepository.save(user));
    }

    /** Legt die Demo-Benutzer an, falls sie fehlen (idempotent). */
    public void ensureDemoUsers() {
        demoUser("ADMIN", "Demo Administrator", "admin@demo.local", Role.ADMIN);
        demoUser("MREUTER", "Marie Reuter", "marie.reuter@demo.local", Role.SALES);
        demoUser("JMEIER", "Jonas Meier", "jonas.meier@demo.local", Role.WAREHOUSE);
        demoUser("PRICHTER", "Paul Richter", "paul.richter@demo.local", Role.BILLING);
        demoUser("AKOCH", "Anna Koch", "anna.koch@demo.local", Role.BILLING);
        demoUser("SWEBER", "Sophie Weber", "sophie.weber@demo.local", Role.CONTROLLING);
    }

    private void demoUser(String username, String fullName, String email, Role role) {
        if (userRepository.findByUsernameIgnoreCase(username).isEmpty()) {
            User user = new User(username, fullName, email, passwordEncoder.encode(DEMO_PASSWORD), EnumSet.of(role));
            user.setDemoAccount(true);
            userRepository.save(user);
        }
    }

    private AuthDtos.UserDto toDto(User user) {
        return new AuthDtos.UserDto(user.getId(), user.getUsername(), user.getFullName(), user.getEmail(),
                roles(user), user.isActive(), user.getLastLoginAt(), user.getCreatedAt());
    }

    private static List<AuthDtos.RoleDto> roles(User user) {
        return user.getRoles().stream().sorted().map(AuthDtos.RoleDto::of).toList();
    }
}
